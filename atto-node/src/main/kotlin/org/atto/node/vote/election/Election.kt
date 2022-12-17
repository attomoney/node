package org.atto.node.vote.election

import kotlinx.coroutines.*
import mu.KotlinLogging
import org.atto.commons.AttoAmount
import org.atto.commons.AttoHash
import org.atto.commons.AttoPublicKey
import org.atto.node.CacheSupport
import org.atto.node.account.Account
import org.atto.node.transaction.PublicKeyHeight
import org.atto.node.transaction.Transaction
import org.atto.node.transaction.TransactionValidated
import org.atto.node.vote.Vote
import org.atto.node.vote.VoteValidated
import org.atto.node.vote.weight.VoteWeightService
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap


@Service
class Election(
    private val properties: ElectionProperties,
    private val voteWeightService: VoteWeightService,
    private val observers: List<ElectionObserver>
) : CacheSupport {
    private val logger = KotlinLogging.logger {}

    /**
     * Refactoring me. Too many potential side effects. Migrate to single thread
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val singleDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val singleScope = CoroutineScope(singleDispatcher)

    private val transactionWeighters = ConcurrentHashMap<PublicKeyHeight, MutableMap<AttoHash, TransactionWeighter>>()

    fun getSize(): Int {
        return transactionWeighters.size
    }

    fun getElections(): Map<PublicKeyHeight, Map<AttoHash, TransactionWeighter>> {
        return transactionWeighters
    }

    @EventListener
    fun observe(event: TransactionValidated) = runBlocking {
        val transaction = event.payload
        observe(event.account, transaction)
    }

    @EventListener
    fun process(event: VoteValidated) = runBlocking {
        val vote = event.payload
        process(event.transaction, vote)
    }

    suspend fun observe(account: Account, transaction: Transaction) {
        transactionWeighters.compute(transaction.toPublicKeyHeight()) { _, v ->
            val weighter = v ?: ConcurrentHashMap()
            weighter[transaction.hash] = TransactionWeighter(account, transaction)
            weighter
        }

        logger.trace { "Started observing $transaction" }

        observers.forEach { it.observed(account, transaction) }
    }

    private fun isObserving(transaction: Transaction): Boolean {
        return transactionWeighters.containsKey(transaction.toPublicKeyHeight())
    }

    private fun stopObserving(transaction: Transaction) {
        transactionWeighters.remove(transaction.toPublicKeyHeight())
    }

    private suspend fun process(transaction: Transaction, vote: Vote) {
        val publicKeyHeight = transaction.toPublicKeyHeight()

        val transactionWeightMap = transactionWeighters[publicKeyHeight] ?: return

        logger.trace { "Processing $vote" }

        val weighter = transactionWeightMap[vote.hash] ?: return
        weighter.add(vote)

        transactionWeightMap.values.asSequence()
            .filter { it != weighter }
            .forEach { _ -> weighter.remove(vote) }

        consensed(publicKeyHeight)

        val minimalConfirmationWeight = voteWeightService.getMinimalConfirmationWeight()

        if (isObserving(transaction) && weighter.isAgreementAbove(minimalConfirmationWeight)) {
            observers.forEach { it.agreed(weighter.account, weighter.transaction) }
        }

        if (isObserving(transaction) && weighter.isFinalAbove(minimalConfirmationWeight)) {
            stopObserving(transaction)

            val votes = weighter.votes.values.filter { it.isFinal() }

            observers.forEach { it.confirmed(weighter.account, transaction, votes) }
        }
    }

    private suspend fun consensed(publicKeyHeight: PublicKeyHeight) {
        val weighter = getConsensus(publicKeyHeight)
        observers.forEach { it.consensed(weighter.account, weighter.transaction) }
    }

    private fun getConsensus(publicKeyHeight: PublicKeyHeight): TransactionWeighter {
        return transactionWeighters[publicKeyHeight]!!.values.asSequence()
            .maxByOrNull { it.totalWeight() }!!
    }


    @Scheduled(cron = "0 0/1 * * * *")
    fun processStaling() {
        val minimalTimestamp = Instant.now().minusSeconds(properties.stalingAfterTimeInSeconds!!)
        singleScope.launch {
            transactionWeighters.values.asSequence()
                .flatMap { it.values }
                .map { it.transaction }
                .filter { it.receivedAt < minimalTimestamp }
                .map { it.toPublicKeyHeight() }
                .distinct()
                .map { getConsensus(it) }
                .forEach { weighter ->
                    logger.trace { "Staling ${weighter.transaction}" }
                    observers.forEach {
                        it.staling(weighter.account, weighter.transaction)
                    }
                }
        }
    }

    @Scheduled(cron = "0 0/1 * * * * *")
    fun stopObservingStaled() {
        val minimalTimestamp = Instant.now().minusSeconds(properties.staledAfterTimeInSeconds!!)
        singleScope.launch {
            transactionWeighters.values.asSequence()
                .flatMap { it.values }
                .filter { it.transaction.receivedAt < minimalTimestamp }
                .forEach { weighter ->
                    logger.trace { "Staled ${weighter.transaction}" }
                    stopObserving(weighter.transaction)
                    observers.forEach {
                        it.staled(weighter.account, weighter.transaction)
                    }
                }
        }
    }

    override fun clear() {
        transactionWeighters.clear()
    }
}

data class TransactionWeighter(val account: Account, val transaction: Transaction) {
    internal val votes = HashMap<AttoPublicKey, Vote>()

    internal fun add(vote: Vote): Boolean {
        val oldVote = votes[vote.publicKey]

        if (oldVote != null && oldVote.timestamp >= vote.timestamp) {
            return false
        }

        votes[vote.publicKey] = vote

        return true
    }

    internal fun totalWeight(): AttoAmount {
        return votes.values.asSequence()
            .map { it.weight }
            .fold(AttoAmount.min) { a1, a2 -> a1 + a2 }
    }

    internal fun totalFinalWeight(): AttoAmount {
        return votes.values.asSequence()
            .filter { it.isFinal() }
            .map { it.weight }
            .fold(AttoAmount.min) { a1, a2 -> a1 + a2 }
    }

    internal fun remove(vote: Vote): Vote? {
        return votes.remove(vote.publicKey)
    }

    internal fun isAgreementAbove(weight: AttoAmount): Boolean {
        return totalWeight().raw >= weight.raw
    }

    internal fun isFinalAbove(weight: AttoAmount): Boolean {
        return totalFinalWeight().raw >= weight.raw
    }
}