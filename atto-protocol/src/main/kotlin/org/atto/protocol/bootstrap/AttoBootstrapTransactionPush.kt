package org.atto.protocol.bootstrap

import org.atto.commons.AttoTransaction
import org.atto.protocol.network.AttoMessage
import org.atto.protocol.network.AttoMessageType


data class AttoBootstrapTransactionPush(val transaction: AttoTransaction) : AttoMessage {
    companion object {
        val size = AttoTransaction.size
    }

    override fun messageType(): AttoMessageType {
        return AttoMessageType.BOOTSTRAP_TRANSACTION_PUSH
    }

}

