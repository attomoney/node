package atto.node.election

import atto.node.EventPublisher
import atto.node.account.Account
import atto.node.network.BroadcastNetworkMessage
import atto.node.network.BroadcastStrategy
import atto.node.network.NetworkMessagePublisher
import atto.node.transaction.*
import atto.node.vote.Vote
import atto.node.vote.VoteValidated
import atto.node.vote.weight.VoteWeighter
import atto.protocol.vote.AttoVote
import atto.protocol.vote.AttoVotePush
import atto.protocol.vote.AttoVoteSignature
import cash.atto.commons.*
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class ElectionVoter(
    private val thisNode: atto.protocol.AttoNode,
    private val privateKey: AttoPrivateKey,
    private val voteWeighter: VoteWeighter,
    private val transactionRepository: TransactionRepository,
    private val eventPublisher: EventPublisher,
    private val messagePublisher: NetworkMessagePublisher
) {
    private val logger = KotlinLogging.logger {}

    val defaultScope = CoroutineScope(Dispatchers.Default + CoroutineName(this.javaClass.simpleName))
    val ioScope = CoroutineScope(Dispatchers.IO + CoroutineName(this.javaClass.simpleName))

    private val minWeight = AttoAmount(1_000_000_000_000_000u)

    private val transactions = ConcurrentHashMap<PublicKeyHeight, Transaction>()
    private val agreements = ConcurrentHashMap.newKeySet<PublicKeyHeight>()

    @PreDestroy
    fun preDestroy() {
        ioScope.cancel()
    }

    @EventListener
    fun process(event: ElectionStarted) {
        val account = event.account
        val transaction = event.transaction
        if (transactions[transaction.toPublicKeyHeight()] == null) {
            consensed(account, transaction)
        }
    }

    @EventListener
    fun process(event: ElectionConsensusChanged) {
        val account = event.account
        val transaction = event.transaction
        consensed(account, transaction)
    }

    fun consensed(account: Account, transaction: Transaction) {
        val publicKeyHeight = transaction.toPublicKeyHeight()

        val oldTransaction = transactions[publicKeyHeight]
        if (oldTransaction != transaction) {
            transactions[publicKeyHeight] = transaction
            vote(transaction, Instant.now())
        }
    }


    @EventListener
    fun process(event: ElectionConsensusReached) {
        val transaction = event.transaction

        val publicKeyHeight = transaction.toPublicKeyHeight()
        if (!agreements.contains(publicKeyHeight)) {
            agreements.add(publicKeyHeight)
            vote(transaction, AttoVoteSignature.finalTimestamp)
        }
    }

    @EventListener
    fun process(event: ElectionFinished) {
        remove(event.transaction)
    }

    @EventListener
    fun process(event: ElectionExpiring) {
        vote(event.transaction, Instant.now())
    }

    @EventListener
    fun process(event: ElectionExpired) {
        remove(event.transaction)
    }

    @EventListener
    fun process(event: TransactionRejected) {
        if (event.reason != TransactionRejectionReason.OLD_TRANSACTION) {
            return
        }
        ioScope.launch {
            val transaction = event.transaction
            if (transactionRepository.existsById(transaction.hash)) {
                vote(transaction, AttoVoteSignature.finalTimestamp)
            }
        }
    }

    private fun vote(transaction: Transaction, timestamp: Instant) {
        val weight = voteWeighter.get()
        if (!canVote(weight)) {
            return
        }

        val voteHash = AttoHash.hash(32, transaction.hash.value, timestamp.toByteArray())
        val voteSignature = AttoVoteSignature(
            timestamp = timestamp,
            publicKey = thisNode.publicKey,
            signature = privateKey.sign(voteHash)
        )
        val attoVote = AttoVote(
            hash = transaction.hash,
            signature = voteSignature,
        )
        val votePush = AttoVotePush(
            vote = attoVote
        )

        val strategy = if (attoVote.signature.isFinal()) {
            BroadcastStrategy.EVERYONE
        } else {
            BroadcastStrategy.VOTERS
        }

        logger.debug { "Sending to $strategy $attoVote" }

        defaultScope.launch {
            eventPublisher.publish(VoteValidated(transaction, Vote.from(weight, attoVote)))
            messagePublisher.publish(BroadcastNetworkMessage(strategy, emptySet(), votePush))
        }
    }

    private fun canVote(weight: AttoAmount): Boolean {
        return thisNode.isVoter() && weight >= minWeight
    }

    private fun remove(transaction: Transaction) {
        val publicKeyHeight = transaction.toPublicKeyHeight()
        transactions.remove(publicKeyHeight)
        agreements.remove(publicKeyHeight)
    }

}