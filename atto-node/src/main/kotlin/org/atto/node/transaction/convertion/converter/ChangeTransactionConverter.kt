package org.atto.node.transaction.convertion.converter

import org.atto.commons.AttoBlockType
import org.atto.commons.AttoChangeBlock
import org.atto.commons.AttoTransaction
import org.atto.node.account.Account
import org.atto.node.transaction.Transaction
import org.atto.node.transaction.convertion.TransactionConversionSupport
import org.springframework.stereotype.Component

@Component
class ChangeTransactionConverter : TransactionConversionSupport {
    override val blockType = AttoBlockType.CHANGE

    override fun toAccountChange(account: Account, transaction: AttoTransaction): Transaction {
        val block = transaction.block as AttoChangeBlock
        return Transaction(
            block = block,
            signature = transaction.signature,
            work = transaction.work
        )
    }

}