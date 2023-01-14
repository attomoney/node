package org.atto.node.vote

import org.atto.commons.AttoAmount
import org.atto.commons.AttoHash
import org.atto.commons.AttoPublicKey
import org.atto.commons.AttoSignature
import org.atto.node.Event
import org.atto.node.transaction.Transaction
import org.atto.protocol.vote.AttoVote
import org.atto.protocol.vote.AttoVoteSignature
import org.springframework.data.annotation.Id
import java.net.InetSocketAddress
import java.time.Instant

data class PublicKeyHash(val publicKey: AttoPublicKey, val hash: AttoHash)

data class Vote(
    val hash: AttoHash,
    val publicKey: AttoPublicKey,
    val timestamp: Instant,
    @Id
    val signature: AttoSignature,
    val weight: AttoAmount,

    val receivedAt: Instant = Instant.now(),
    val persistedAt: Instant? = null,
) {

    companion object {
        fun from(weight: AttoAmount, attoVote: AttoVote): Vote {
            return Vote(
                hash = attoVote.hash,
                publicKey = attoVote.signature.publicKey,
                timestamp = attoVote.signature.timestamp,
                signature = attoVote.signature.signature,
                weight = weight,
            )
        }
    }

    fun isFinal(): Boolean {
        return AttoVoteSignature.finalTimestamp == timestamp
    }

    fun toPublicKeyHash(): PublicKeyHash {
        return PublicKeyHash(publicKey, hash)
    }

    fun toAttoVote(): AttoVote {
        val voteSignature = AttoVoteSignature(
            timestamp = timestamp,
            publicKey = publicKey,
            signature = signature
        )

        return AttoVote(
            hash = hash,
            signature = voteSignature
        )
    }
}

data class VoteReceived(
    val socketAddress: InetSocketAddress,
    val vote: Vote
) : Event

data class VoteValidated(
    val transaction: Transaction,
    val vote: Vote
) : Event

enum class VoteDropReason {
    SUPERSEDED, NO_ELECTION, TRANSACTION_DROPPED
}

data class VoteDropped(
    val vote: Vote,
    val reason: VoteDropReason
) : Event

enum class VoteRejectionReason {
    INVALID_VOTING_WEIGHT,
}

data class VoteRejected(
    val reason: VoteRejectionReason,
    val vote: Vote
) : Event