package org.atto.protocol.network.codec.transaction

import org.atto.commons.AttoByteBuffer
import org.atto.protocol.network.AttoMessageType
import org.atto.protocol.network.codec.AttoMessageCodec
import org.atto.protocol.transaction.AttoTransactionPush
import org.atto.protocol.transaction.AttoTransactionResponse

class AttoTransactionResponseCodec(private val transactionCodec: AttoTransactionCodec) :
    AttoMessageCodec<AttoTransactionResponse> {

    override fun messageType(): AttoMessageType {
        return AttoMessageType.TRANSACTION_RESPONSE
    }

    override fun targetClass(): Class<AttoTransactionResponse> {
        return AttoTransactionResponse::class.java
    }

    override fun fromByteBuffer(byteBuffer: AttoByteBuffer): AttoTransactionResponse? {
        if (byteBuffer.size < AttoTransactionPush.size) {
            return null
        }

        val transaction = transactionCodec.fromByteBuffer(byteBuffer) ?: return null

        return AttoTransactionResponse(
            transaction = transaction
        )
    }

    override fun toByteBuffer(t: AttoTransactionResponse): AttoByteBuffer {
        return transactionCodec.toByteBuffer(t.transaction)
    }
}