@file:Suppress("PackageDirectoryMismatch")

package vip.cdms.drsticker.rule.preprocess

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import vip.cdms.drsticker.data.StickerId
import vip.cdms.drsticker.data.StickerSetId
import java.io.OutputStream
import java.security.MessageDigest

class ProcessingSticker(
    val bytes: ByteArray,
    val extension: String,
)

@Serializable
sealed interface RulesetPreprocess
interface PreprocessHandler<C : RulesetPreprocess> {
    suspend fun process(config: C, input: ProcessingSticker): ProcessingSticker?
}


@Suppress("unused")
@Serializable
class PreprocessCacheKey(
    val setId: StickerSetId,
    val stickerId: StickerId,
    val preprocesses: List<RulesetPreprocess>,
)

@OptIn(ExperimentalSerializationApi::class)
fun PreprocessCacheKey.toHash(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Json.encodeToStream(this, object : OutputStream() {
        override fun write(b: Int) =
            digest.update(b.toByte())

        override fun write(b: ByteArray, off: Int, len: Int) =
            digest.update(b, off, len)
    })
    return digest.digest().toHexString()
}
