package org.atto.node.bootstrap.unchecked

import com.fasterxml.jackson.annotation.JsonIgnore
import org.atto.commons.AttoBlock
import org.atto.commons.AttoHash
import org.atto.commons.AttoSignature
import org.atto.commons.AttoWork
import org.atto.node.transaction.Transaction
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table
data class UncheckedTransaction(
    val block: AttoBlock,
    val signature: AttoSignature,
    val work: AttoWork,
    var receivedAt: Instant,
    var persistedAt: Instant? = null,
) : Persistable<AttoHash> {

    @Id
    @JsonIgnore
    val hash = block.hash

    @JsonIgnore
    val publicKey = block.publicKey

    @JsonIgnore
    val height = block.height


    override fun getId(): AttoHash {
        return hash
    }

    override fun isNew(): Boolean {
        return persistedAt == null
    }

    fun toTransaction(): Transaction {
        return Transaction(
            block = block,
            signature = signature,
            work = work,
            receivedAt = receivedAt
        )
    }

    override fun toString(): String {
        return "UncheckedTransaction(block=$block, signature=$signature, work=$work, receivedAt=$receivedAt, persistedAt=$persistedAt, hash=$hash, publicKey=$publicKey, height=$height)"
    }
}

fun Transaction.toUncheckedTransaction(): UncheckedTransaction {
    return UncheckedTransaction(
        block = block,
        signature = signature,
        work = work,
        receivedAt = receivedAt
    )
}