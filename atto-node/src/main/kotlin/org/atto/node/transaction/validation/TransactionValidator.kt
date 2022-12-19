package org.atto.node.transaction.validation

import org.atto.node.account.Account
import org.atto.node.transaction.Transaction
import org.atto.node.transaction.TransactionRejectionReason

interface TransactionValidator {

    fun supports(change: Transaction): Boolean

    suspend fun validate(account: Account, change: Transaction): TransactionViolation?
}

data class TransactionViolation(val reason: TransactionRejectionReason, val message: String)