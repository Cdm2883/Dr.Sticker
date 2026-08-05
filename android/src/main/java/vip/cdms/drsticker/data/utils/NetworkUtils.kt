package vip.cdms.drsticker.data.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Sink
import okio.buffer

fun OkHttpClient.fetchAsString(url: String): String {
    val request = Request.Builder().url(url).build()
    return newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("Unexpected $response")
        response.body.string()
    }
}

fun OkHttpClient.fetchToSink(url: String, out: Sink) {
    val request = Request.Builder().url(url).build()
    newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("Unexpected $response")
        val bufferedSink = out.buffer()
        bufferedSink.writeAll(response.body.source())
        bufferedSink.flush()
    }
}
