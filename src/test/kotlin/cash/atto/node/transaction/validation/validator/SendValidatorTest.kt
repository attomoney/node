package cash.atto.node.transaction.validation.validator

import cash.atto.commons.*
import cash.atto.node.account.Account
import cash.atto.node.transaction.Transaction
import cash.atto.node.transaction.TransactionRejectionReason
import cash.atto.protocol.AttoNode
import cash.atto.protocol.NodeFeature
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.random.Random

internal class SendValidatorTest {
    val privateKey = AttoPrivateKey.generate()

    val account =
        Account(
            publicKey = privateKey.toPublicKey(),
            network = AttoNetwork.LOCAL,
            algorithm = AttoAlgorithm.V1,
            version = 0U.toAttoVersion(),
            height = 2U.toAttoHeight(),
            balance = AttoAmount(100u),
            lastTransactionHash = AttoHash(ByteArray(32)),
            lastTransactionTimestamp = AttoNetwork.INITIAL_INSTANT.toJavaInstant(),
            representative = AttoPublicKey(ByteArray(32)),
        )
    val block =
        AttoSendBlock(
            network = AttoNetwork.LOCAL,
            version = account.version,
            algorithm = AttoAlgorithm.V1,
            publicKey = privateKey.toPublicKey(),
            height = account.height + 1U,
            balance = AttoAmount(0u),
            timestamp = account.lastTransactionTimestamp.plusSeconds(1).toKotlinInstant(),
            previous = account.lastTransactionHash,
            receiverAlgorithm = AttoAlgorithm.V1,
            receiverPublicKey = privateKey.toPublicKey(),
            amount = AttoAmount(100u),
        )

    val node =
        AttoNode(
            network = AttoNetwork.LOCAL,
            protocolVersion = 0u,
            algorithm = AttoAlgorithm.V1,
            publicKey = AttoPublicKey(Random.nextBytes(ByteArray(32))),
            publicUri = URI("ws://localhost:8081"),
            features = setOf(NodeFeature.VOTING, NodeFeature.HISTORICAL),
        )

    val transaction =
        Transaction(
            block,
            privateKey.sign(block.hash),
            AttoWorker.cpu().work(block),
        )

    private val validator = SendValidator()

    @Test
    fun `should validate`() =
        runBlocking {
            // when
            val violation = validator.validate(account, transaction)

            // then
            assertNull(violation)
        }

    @Test
    fun `should return INVALID_AMOUNT when account height is not immediately before`() =
        runBlocking {
            // when
            val violation = validator.validate(account.copy(balance = account.balance + AttoAmount(1UL)), transaction)

            // then
            assertEquals(TransactionRejectionReason.INVALID_AMOUNT, violation?.reason)
        }
}
