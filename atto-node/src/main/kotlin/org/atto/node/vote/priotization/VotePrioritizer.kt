package org.atto.node.vote.priotization

import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.atto.commons.AttoAmount
import org.atto.commons.AttoHash
import org.atto.commons.AttoPublicKey
import org.atto.commons.AttoSignature
import org.atto.node.CacheSupport
import org.atto.node.DuplicateDetector
import org.atto.node.EventPublisher
import org.atto.node.election.ElectionExpired
import org.atto.node.election.ElectionStarted
import org.atto.node.transaction.Transaction
import org.atto.node.transaction.TransactionSaved
import org.atto.node.vote.*
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class VotePrioritizer(
    properties: VotePrioritizationProperties,
    private val eventPublisher: EventPublisher,
) : CacheSupport {
    private val logger = KotlinLogging.logger {}

    private lateinit var job: Job

    @OptIn(ExperimentalCoroutinesApi::class)
    private val singleDispatcher = Dispatchers.Default.limitedParallelism(1)

    private val queue = VoteQueue(properties.groupMaxSize!!)

    private val activeElections = ConcurrentHashMap<AttoHash, Transaction>()

    private val duplicateDetector = DuplicateDetector<AttoSignature>()

    private val voteBuffer: MutableMap<AttoHash, MutableMap<AttoPublicKey, Vote>> = Caffeine.newBuilder()
        .maximumWeight(properties.cacheMaxSize!!.toLong())
        .weigher { _: AttoHash, v: MutableMap<AttoPublicKey, Vote> -> v.size }
        .removalListener { _: AttoHash?, votes: MutableMap<AttoPublicKey, Vote>?, _ ->
            votes?.values?.forEach {
                eventPublisher.publish(VoteDropped(it, VoteDropReason.NO_ELECTION))
            }
        }
        .build<AttoHash, MutableMap<AttoPublicKey, Vote>>()
        .asMap()

    fun getQueueSize(): Int {
        return queue.getSize()
    }

    fun getBufferSize(): Int {
        return voteBuffer.size
    }

    @EventListener
    @Async
    fun process(event: ElectionStarted) {
        val transaction = event.transaction

        activeElections[transaction.hash] = transaction

        val votes = voteBuffer.remove(transaction.hash)?.values ?: setOf()

        runBlocking(singleDispatcher) {
            votes.forEach {
                logger.trace { "Unbuffered vote and ready to be prioritized. $it" }
                add(it)
            }
        }
    }

    @EventListener
    fun process(event: ElectionExpired) {
        activeElections.remove(event.transaction.hash)
    }

    @EventListener
    @Async
    fun process(event: TransactionSaved) {
        activeElections.remove(event.transaction.hash)
    }

    @EventListener
    fun add(event: VoteReceived) {
        val vote = event.vote

        if (duplicateDetector.isDuplicate(vote.signature)) {
            logger.trace { "Ignored duplicated $vote" }
            return
        }

        runBlocking {
            add(vote)
        }
    }

    private suspend fun add(vote: Vote) {
        val transaction = activeElections[vote.hash]
        if (transaction != null) {
            logger.trace { "Queued for prioritization. $vote" }

            val droppedVote = withContext(singleDispatcher) {
                queue.add(VoteQueue.TransactionVote(transaction, vote))
            }

            droppedVote?.let {
                logger.trace { "Dropped from queue. $droppedVote" }
                eventPublisher.publish(VoteDropped(droppedVote.vote, VoteDropReason.SUPERSEDED))
            }
        } else {
            logger.trace { "Buffered until election starts. $vote" }
            voteBuffer.compute(vote.hash) { _, m ->
                val map = m ?: HashMap()
                map.compute(vote.publicKey) { _, v ->
                    if (v == null || vote.timestamp > v.timestamp) {
                        vote
                    } else {
                        v
                    }
                }
                map
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @PostConstruct
    fun start() {
        job = GlobalScope.launch(CoroutineName("vote-prioritizer")) {
            while (isActive) {
                val transactionVote = withContext(singleDispatcher) {
                    queue.poll()
                }

                if (transactionVote != null) {
                    val transaction = transactionVote.transaction
                    val vote = transactionVote.vote

                    val rejectionReason = validate(vote)

                    if (rejectionReason != null) {
                        eventPublisher.publish(VoteRejected(rejectionReason, vote))
                    } else {
                        eventPublisher.publish(VoteValidated(transaction, vote))
                    }
                } else {
                    delay(100)
                }
            }
        }
    }

    @PreDestroy
    fun stop() {
        job.cancel()
    }

    private fun validate(vote: Vote): VoteRejectionReason? {
        if (vote.weight == AttoAmount.MIN) {
            return VoteRejectionReason.INVALID_VOTING_WEIGHT
        }

        return null
    }

    override fun clear() {
        queue.clear()
        activeElections.clear()
        voteBuffer.clear()
        duplicateDetector.clear()
    }
}