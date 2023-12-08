package atto.node.bootstrap.discovery

import atto.node.EventPublisher
import atto.node.bootstrap.TransactionDiscovered
import atto.node.bootstrap.unchecked.GapView
import atto.node.network.InboundNetworkMessage
import atto.node.network.NetworkMessagePublisher
import atto.node.network.OutboundNetworkMessage
import atto.node.network.peer.PeerAdded
import atto.node.network.peer.PeerRemoved
import atto.node.transaction.toTransaction
import atto.protocol.transaction.AttoTransactionStreamRequest
import atto.protocol.transaction.AttoTransactionStreamResponse
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.PreviousSupport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.reactive.asFlow
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@Component
class GapDiscoverer(
    private val databaseClient: DatabaseClient,
    private val networkMessagePublisher: NetworkMessagePublisher,
    private val eventPublisher: EventPublisher
) {
    private val logger = KotlinLogging.logger {}

    @OptIn(ExperimentalCoroutinesApi::class)
    private val singleScope =
        CoroutineScope(Dispatchers.IO.limitedParallelism(1) + CoroutineName(this.javaClass.simpleName))

    private val peers = ConcurrentHashMap.newKeySet<InetSocketAddress>()

    private val currentHashMap = HashMap<AttoPublicKey, TransactionPointer>()


    @EventListener
    @Async
    fun add(peerEvent: PeerAdded) {
        val peer = peerEvent.peer
        if (!peer.node.isHistorical()) {
            return
        }
        peers.add(peer.connectionSocketAddress)
    }

    @EventListener
    @Async
    fun remove(peerEvent: PeerRemoved) {
        val peer = peerEvent.peer
        peers.remove(peer.connectionSocketAddress)
    }

    @Scheduled(cron = "0 0/1 * * * *")
    fun resolve() {
        val peers = this.peers.toList()

        singleScope.launch {
            val gaps = databaseClient.sql(
                """
                                SELECT public_key, account_height, transaction_height, previous_transaction_hash FROM (
                                        SELECT  ROW_NUMBER() OVER(PARTITION BY ut.public_key ORDER BY ut.height DESC) AS row_num,
                                                ut.public_key public_key,
                                                COALESCE(a.height, 0) account_height,
                                                ut.height transaction_height,
                                                ut.previous previous_transaction_hash
                                        FROM unchecked_transaction ut
                                        LEFT JOIN account a on ut.public_key = a.public_key and ut.height > a.height
                                        ORDER BY ut.public_key, ut.height ) ready
                                WHERE transaction_height > account_height + row_num
                                AND row_num = 1
                """
            ).map { row, metadata ->
                GapView(
                    AttoPublicKey(row.get("public_key", ByteArray::class.java)!!),
                    row.get("account_height", Long::class.javaObjectType)!!.toULong(),
                    row.get("transaction_height", Long::class.javaObjectType)!!.toULong(),
                    AttoHash(row.get("previous_transaction_hash", ByteArray::class.java)!!)
                )
            }.all().asFlow() // https://github.com/spring-projects/spring-data-relational/issues/1394

            gaps.filter { currentHashMap[it.publicKey] == null }
                .onEach {
                    currentHashMap[it.publicKey] = TransactionPointer(
                        it.fromHeight(),
                        it.toHeight(),
                        it.previousTransactionHash
                    )
                }
                .map { AttoTransactionStreamRequest(it.publicKey, it.fromHeight(), it.toHeight()) }
                .map { OutboundNetworkMessage(peers[Random.nextInt(peers.size)], it) }
                .collect { networkMessagePublisher.publish(it) }
        }
    }

    @EventListener
    @Async
    fun process(message: InboundNetworkMessage<AttoTransactionStreamResponse>) {
        val response = message.payload
        val transactions = response.transactions

        val publicKeys = transactions.map { it.block.publicKey }.toSet()
        if (publicKeys.size != 1) {
            return
        }

        singleScope.launch {
            for (transaction in transactions) {
                val block = transaction.block
                val pointer = currentHashMap[block.publicKey]
                if (transaction.hash != pointer?.currentHash) {
                    return@launch
                }
                if (pointer.initialHeight == block.height) {
                    currentHashMap.remove(block.publicKey)
                } else if (block is PreviousSupport) {
                    currentHashMap[block.publicKey] = TransactionPointer(
                        pointer.initialHeight,
                        pointer.currentHeight - 1UL,
                        block.previous
                    )
                }
                logger.debug { "Discovered gap transaction ${transaction.hash}" }
                eventPublisher.publish(TransactionDiscovered(null, transaction.toTransaction(), listOf()))
            }
        }
    }

}

private data class TransactionPointer(val initialHeight: ULong, val currentHeight: ULong, val currentHash: AttoHash)

private fun GapView.fromHeight(): ULong {
    val maxCount = AttoTransactionStreamResponse.maxCount.toULong()
    val count = this.transactionHeight - this.accountHeight
    if (count > maxCount) {
        return this.transactionHeight - maxCount
    }
    return this.accountHeight + 1U
}

private fun GapView.toHeight(): ULong {
    return this.transactionHeight
}