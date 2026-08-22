package vip.cdms.drsticker.data.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.Sink
import okio.buffer

fun OkHttpClient.fetchAsString(url: String): String {
    val request = Request.Builder().url(url).build()
    return newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("Unexpected $response")
        response.body.string()
    }
}

suspend fun OkHttpClient.fetchToSink(url: String, out: Sink) = progressable {
    val request = Request.Builder().url(url).build()
    newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("Unexpected $response")

        val body = response.body
        val contentLength = body.contentLength()
        val source = body.source()
        val bufferedSink = out.buffer()
        val buffer = Buffer()
        var downloadedBytes = 0L

        while (true) {
            val byteCount = source.read(buffer, 8 * 1024L)
            if (byteCount == -1L) break

            bufferedSink.write(buffer, byteCount)
            downloadedBytes += byteCount
            val fraction = if (contentLength > 0L) {
                (downloadedBytes.toDouble() / contentLength)
                    .toFloat()
                    .coerceIn(0f, 1f)
            } else {
                null
            }
            report(fraction)
        }

        bufferedSink.flush()
    }
}
