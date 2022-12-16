package org.atto.commons

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

object AttoSignatures {

    fun sign(privateKey: AttoPrivateKey, hash: ByteArray): AttoSignature {
        val parameters = Ed25519PrivateKeyParameters(privateKey.value, 0)
        val signer = Ed25519Signer()
        signer.init(true, parameters)
        signer.update(hash, 0, hash.size)
        return AttoSignature(signer.generateSignature())
    }

    fun isValid(publicKey: AttoPublicKey, signature: AttoSignature, hash: ByteArray): Boolean {
        val parameters = Ed25519PublicKeyParameters(publicKey.value, 0)
        val signer = Ed25519Signer()
        signer.init(false, parameters)
        signer.update(hash, 0, hash.size)
        return signer.verifySignature(signature.value)
    }

}

data class AttoSignature(val value: ByteArray) {
    companion object {
        val size = 64

        fun parse(value: String): AttoSignature {
            return AttoSignature(value.fromHexToByteArray())
        }
    }

    init {
        value.checkLength(size)
    }

    fun isValid(publicKey: AttoPublicKey, hash: ByteArray): Boolean {
        return AttoSignatures.isValid(publicKey, this, hash)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AttoSignature

        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }


    override fun toString(): String {
        return value.toHex()
    }
}


fun AttoPrivateKey.sign(hash: ByteArray): AttoSignature {
    return AttoSignatures.sign(this, hash)
}