package atto.node.transaction.validation.validator

import atto.node.account.Account
import atto.node.transaction.Transaction
import atto.node.transaction.TransactionRejectionReason
import cash.atto.commons.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.random.Random

internal class PreviousValidatorTest {
    val privateKey = AttoPrivateKey.generate()

    val account = Account(
        publicKey = privateKey.toPublicKey(),
        version = 0u,
        height = 2u,
        balance = AttoAmount(0u),
        lastTransactionHash = AttoHash(ByteArray(32)),
        lastTransactionTimestamp = AttoNetwork.INITIAL_INSTANT,
        representative = AttoPublicKey(ByteArray(32))
    )
    val block = AttoChangeBlock(
        version = account.version,
        publicKey = privateKey.toPublicKey(),
        height = account.height + 1U,
        balance = AttoAmount(0U),
        timestamp = account.lastTransactionTimestamp.plusSeconds(1),
        previous = account.lastTransactionHash,
        representative = privateKey.toPublicKey()
    )

    val node = atto.protocol.AttoNode(
        network = AttoNetwork.LOCAL,
        protocolVersion = 0u,
        publicKey = AttoPublicKey(Random.nextBytes(ByteArray(32))),
        socketAddress = InetSocketAddress(InetAddress.getLocalHost(), 8330),
        features = setOf(atto.protocol.NodeFeature.VOTING, atto.protocol.NodeFeature.HISTORICAL)
    )

    val transaction = Transaction(
        block,
        privateKey.sign(block.hash),
        AttoWork.work(node.network, block.timestamp, block.previous)
    )

    private val validator = PreviousValidator();

    @Test
    fun `should validate`() = runBlocking {
        // when
        val violation = validator.validate(account, transaction)

        // then
        assertNull(violation)
    }


    @Test
    fun `should return INVALID_PREVIOUS when previous is different`() = runBlocking {
        // when
        val byteArray = ByteArray(32)
        byteArray.fill(1)
        val violation = validator.validate(account.copy(lastTransactionHash = AttoHash(byteArray)), transaction)

        // then
        assertEquals(TransactionRejectionReason.INVALID_PREVIOUS, violation?.reason)
    }
}