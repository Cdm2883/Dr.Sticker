package vip.cdms.drsticker.ui.utils

fun Throwable.readableMessage() =
    message?.takeIf { it.isNotBlank() } ?: this::class.simpleName ?: "Unknown error"
