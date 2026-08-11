package vip.cdms.drsticker.rule.utils

import android.webkit.MimeTypeMap

private val extendedMimeTypeMap = mapOf(
    "tgs" to "application/x-tgsticker",
    "webm" to "video/webm",
)

fun String.getMimeTypeFromExtension() =
    extendedMimeTypeMap[lowercase()]
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(lowercase())
        ?: "application/octet-stream"
