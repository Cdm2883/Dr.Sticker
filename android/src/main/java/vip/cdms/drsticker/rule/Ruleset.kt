package vip.cdms.drsticker.rule

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import vip.cdms.drsticker.rule.adapters.RulesetAdapter
import vip.cdms.drsticker.rule.conditions.RulesetCondition
import vip.cdms.drsticker.rule.preprocess.RulesetPreprocess
import vip.cdms.drsticker.rule.triggers.RulesetTrigger

typealias RulesetId = String

@Serializable(with = RulesetIndexEntrySerializer::class)
data class RulesetIndexEntry(
    val rulesetId: RulesetId,
    val isEnabled: Boolean,
)

@Serializable
data class Ruleset(
    val rulesetId: RulesetId,
    val displayName: String,
    val description: String?,
    val condition: RulesetCondition,
    val trigger: RulesetTrigger,
    val preprocesses: List<RulesetPreprocess>,
    val adapter: RulesetAdapter,
)


object RulesetIndexEntrySerializer : KSerializer<RulesetIndexEntry> {
    override val descriptor =
        buildClassSerialDescriptor("RulesetIndexEntry")

    override fun serialize(encoder: Encoder, value: RulesetIndexEntry) =
        requireJsonEncoder(encoder).encodeJsonElement(
            buildJsonArray {
                add(value.rulesetId)
                add(value.isEnabled)
            }
        )

    override fun deserialize(decoder: Decoder): RulesetIndexEntry {
        val array = runCatching { requireJsonDecoder(decoder).decodeJsonElement().jsonArray }
            .getOrElse { throw SerializationException("Ruleset index entry must be a JSON array.", it) }
        if (array.size != 2)
            throw SerializationException("Ruleset index entry must contain exactly two elements.")
        return runCatching {
            RulesetIndexEntry(
                rulesetId = array[0].jsonPrimitive.content,
                isEnabled = array[1].jsonPrimitive.boolean,
            )
        }.getOrElse {
            throw SerializationException("Invalid element types in ruleset index entry.", it)
        }
    }

    private fun requireJsonEncoder(encoder: Encoder) = encoder as? JsonEncoder
        ?: throw SerializationException("RulesetIndexEntry only supports JSON serialization.")

    private fun requireJsonDecoder(decoder: Decoder) = decoder as? JsonDecoder
        ?: throw SerializationException("RulesetIndexEntry only supports JSON deserialization.")
}
