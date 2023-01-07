package org.atto.node.transaction

import com.fasterxml.jackson.annotation.JsonIgnore
import org.atto.commons.*
import org.atto.node.Event
import org.atto.node.account.Account
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import java.time.Instant

data class PublicKeyHeight(val publicKey: AttoPublicKey, val height: ULong)

data class Transaction(
    val block: AttoBlock,
    val signature: AttoSignature,
    val work: AttoWork,
    val receivedAt: Instant = Instant.now(),
    val persistedAt: Instant? = null,
) : Persistable<AttoHash> {
    @Id
    @JsonIgnore
    val hash = block.hash

    @JsonIgnore
    val publicKey = block.publicKey

    @JsonIgnore
    override fun getId(): AttoHash {
        return hash
    }

    @JsonIgnore
    override fun isNew(): Boolean {
        return persistedAt == null
    }

    fun toAttoTransaction(): AttoTransaction {
        return AttoTransaction(
            block = block,
            signature = signature,
            work = work
        )
    }

    fun toPublicKeyHeight(): PublicKeyHeight {
        return PublicKeyHeight(this.block.publicKey, this.block.height)
    }

    override fun toString(): String {
        return "Transaction(hash=$hash, publicKey=$publicKey, block=$block, signature=$signature, work=$work, receivedAt=$receivedAt, persistedAt=$persistedAt)"
    }
}

fun AttoTransaction.toTransaction(): Transaction {
    return Transaction(
        block = block,
        signature = signature,
        work = work
    )
}

data class TransactionReceived(val transaction: Transaction) : Event

data class TransactionDropped(val transaction: Transaction) : Event
data class TransactionValidated(
    val account: Account,
    val transaction: Transaction
) : Event

data class TransactionSaved(
    val previousAccount: Account,
    val updatedAccount: Account,
    val transaction: Transaction
) : Event

enum class TransactionRejectionReason(val recoverable: Boolean) {
    INVALID_TRANSACTION(false),
    INVALID_BALANCE(false),
    INVALID_AMOUNT(false),
    INVALID_RECEIVER(false),
    INVALID_CHANGE(false),
    INVALID_TIMESTAMP(false),
    INVALID_VERSION(false),
    INVALID_PREVIOUS(false),
    INVALID_REPRESENTATIVE(false),
    ACCOUNT_NOT_FOUND(true),
    PREVIOUS_NOT_FOUND(true),
    SEND_NOT_FOUND(true),
    SEND_NOT_CONFIRMED(true),
    SEND_ALREADY_USED(false),
    OLD_TRANSACTION(false),
}

data class TransactionRejected(
    val reason: TransactionRejectionReason,
    val message: String,
    val account: Account,
    val transaction: Transaction
) : Event