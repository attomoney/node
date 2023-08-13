package atto.node.convertion

import cash.atto.commons.AttoSignature
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

@Component
@ConfigurationPropertiesBinding
class AttoSignatureConverter : Converter<String, AttoSignature> {
    override fun convert(source: String): AttoSignature {
        return AttoSignature.parse(source)
    }
}

@Component
class AttoSignatureStdSerializer : StdSerializer<AttoSignature>(AttoSignature::class.java) {
    override fun serialize(value: AttoSignature, generator: JsonGenerator, provider: SerializerProvider) {
        generator.writeString(value.toString())
    }
}

@Component
class AttoSignatureStdDeserializer : StdDeserializer<AttoSignature>(AttoSignature::class.java) {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): AttoSignature {
        val value = parser.readValueAs(String::class.java)
        return AttoSignature.parse(value)
    }
}


@Component
class AttoSignatureSerializerDBConverter : DBConverter<AttoSignature, ByteArray> {
    override fun convert(source: AttoSignature): ByteArray {
        return source.value;
    }
}

@Component
class AttoSignatureDeserializerDBConverter : DBConverter<ByteArray, AttoSignature> {
    override fun convert(source: ByteArray): AttoSignature {
        return AttoSignature(source)
    }
}

