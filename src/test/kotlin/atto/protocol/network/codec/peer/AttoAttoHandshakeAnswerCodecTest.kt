package atto.protocol.network.codec.peer

import atto.protocol.network.codec.AttoNodeCodec
import atto.protocol.network.codec.peer.handshake.AttoHandshakeAnswerCodec
import atto.protocol.network.handshake.AttoHandshakeAnswer
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoSignature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.random.Random

internal class AttoAttoHandshakeAnswerCodecTest {
    val nodeCodec = AttoNodeCodec()
    val codec = AttoHandshakeAnswerCodec(nodeCodec)

    @Test
    fun `should serialize and deserialize`() {
        // given
        val node = atto.protocol.AttoNode(
            network = AttoNetwork.LIVE,
            protocolVersion = 0u,
            algorithm = AttoAlgorithm.V1,
            publicKey = AttoPublicKey(Random.nextBytes(ByteArray(32))),
            socketAddress = InetSocketAddress(InetAddress.getLocalHost(), 8330),
            features = setOf(atto.protocol.NodeFeature.VOTING, atto.protocol.NodeFeature.HISTORICAL)
        )

        val expectedHandshakeAnswer = AttoHandshakeAnswer(
            signature = AttoSignature(Random.nextBytes(ByteArray(64))),
            node = node
        )

        // when
        val byteBuffer = codec.toByteBuffer(expectedHandshakeAnswer)
        val handshakeAnswer = codec.fromByteBuffer(byteBuffer)

        // then
        assertEquals(expectedHandshakeAnswer, handshakeAnswer)
    }
}