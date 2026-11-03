package com.loop.launcher.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(val code: Int, val body: String)

fun interface HttpGet {
    @Throws(IOException::class)
    fun get(url: String): HttpResponse
}

class UrlConnectionHttpGet : HttpGet {
    override fun get(url: String): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "LoopLauncher")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
