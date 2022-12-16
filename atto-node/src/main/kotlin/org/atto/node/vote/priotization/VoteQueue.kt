package org.atto.node.vote.priotization

import org.atto.node.transaction.Transaction
import org.atto.node.vote.PublicKeyHash
import org.atto.node.vote.Vote
import java.util.*
import java.util.Comparator.comparing


class VoteQueue(private val maxSize: Int) {
    private val weightComparator: Comparator<TransactionVote> = comparing { it.vote.weight.raw }

    private val map = HashMap<PublicKeyHash, TransactionVote>()
    private val set = TreeSet(weightComparator)
    private var size = 0

    fun add(entry: TransactionVote): TransactionVote? {
        val vote = entry.vote
        val publicKeyHash = vote.toPublicKeyHash()

        val oldEntry = map.remove(publicKeyHash)
        val oldVote = oldEntry?.vote
        if (oldVote != null && oldVote.timestamp > vote.timestamp) {
            map[publicKeyHash] = oldEntry
            return entry
        }

        if (oldEntry != null) {
            set.remove(oldEntry)
        }

        map[publicKeyHash] = entry

        if (set.add(entry)) {
            size++
        }

        if (oldEntry == null && set.size > maxSize) {
            size--
            val removedEntry = set.pollFirst()!!
            return map.remove(removedEntry.vote.toPublicKeyHash())
        }

        return null
    }

    fun poll(): TransactionVote? {
        val entry = set.pollLast()

        if (entry != null) {
            size--
            map.remove(entry.vote.toPublicKeyHash())
        }

        return entry
    }

    fun getSize(): Int {
        return size
    }

    fun clear() {
        map.clear()
        set.clear()
        size = 0
    }

    public data class TransactionVote(
        val transaction: Transaction,
        val vote: Vote
    )
}