package com.linda.app.core.net

import java.net.HttpURLConnection
import java.net.URL

/** The app's only network code: two tiny calls on the platform's own HttpURLConnection (no extra library). */
object Http {
    data class Response(val code: Int, val body: String)

    /** Returns null when the server cannot be reached at all. */
    fun postJson(url: String, json: String, timeoutMs: Int = 8_000): Response? = call(url, "POST", json, timeoutMs)
    fun get(url: String, timeoutMs: Int = 8_000): Response? = call(url, "GET", null, timeoutMs)

    private fun call(url: String, method: String, json: String?, timeoutMs: Int): Response? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        if (json != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(json.toByteArray()) }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        Response(code, body)
    } catch (e: Exception) {
        null
    }
}
