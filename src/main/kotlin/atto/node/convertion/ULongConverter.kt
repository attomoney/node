package atto.node.convertion

import atto.node.toBigInteger
import atto.node.toULong
import org.springframework.stereotype.Component
import java.math.BigInteger

@Component
class ULongSerializerDBConverter : DBConverter<ULong, BigInteger> {
    override fun convert(source: ULong): BigInteger = source.toBigInteger()
}

@Component
class ULongDeserializerDBConverter : DBConverter<BigInteger, ULong> {
    override fun convert(source: BigInteger): ULong = source.toULong()
}
