package com.loop.explorer.server

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

class Request(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: InputStream,
    val length: Long,
    /** Address of the device that asked. */
    val remote: String? = null,
) {
    fun param(name: String): String? = query[name]

    fun json(): JSONObject = runCatching { JSONObject(body.readBytes().toString(Charsets.UTF_8)) }.getOrDefault(JSONObject())
}

class Response(
    val status: Int,
    val type: String,
    val length: Long = -1,
    val headers: Map<String, String> = emptyMap(),
    val write: (OutputStream) -> Unit,
) {
    companion object {
        fun bytes(data: ByteArray, type: String, status: Int = 200, headers: Map<String, String> = emptyMap()) =
            Response(status, type, data.size.toLong(), headers) { it.write(data) }

        fun text(text: String, type: String = "text/plain; charset=utf-8", status: Int = 200) =
            bytes(text.toByteArray(Charsets.UTF_8), type, status)

        fun json(value: Any, status: Int = 200): Response = text(
            when (value) {
                is JSONObject, is JSONArray -> value.toString()
                else -> JSONObject.wrap(value).toString()
            },
            "application/json; charset=utf-8",
            status,
        )

        fun error(status: Int, message: String) = json(JSONObject().put("error", message), status)

        /** Sends a file, or the part the browser asked for, so videos can jump to any point. */
        fun file(file: File, type: String, range: String?, download: Boolean): Response {
            val size = file.length()
            val name = file.name.replace("\"", "")
            val disposition = (if (download) "attachment" else "inline") + "; filename=\"" + name + "\"; filename*=UTF-8''" +
                java.net.URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
            val parts = range?.removePrefix("bytes=")?.split('-')
            val start = parts?.getOrNull(0)?.toLongOrNull()
            if (parts == null || start == null || start >= size) {
                return Response(200, type, size, mapOf("Accept-Ranges" to "bytes", "Content-Disposition" to disposition)) { out ->
                    file.inputStream().use { it.copyTo(out, BUFFER) }
                }
            }
            val end = (parts.getOrNull(1)?.toLongOrNull() ?: (size - 1)).coerceAtMost(size - 1)
            if (end < start) return file(file, type, null, download)
            val count = end - start + 1
            return Response(
                206,
                type,
                count,
                mapOf("Accept-Ranges" to "bytes", "Content-Range" to "bytes $start-$end/$size", "Content-Disposition" to disposition),
            ) { out ->
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(start)
                    val buffer = ByteArray(BUFFER)
                    var left = count
                    while (left > 0) {
                        val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        left -= read
                    }
                }
            }
        }

        const val BUFFER = 64 * 1024
    }
}

/**
 * A small HTTP/1.1 server for the local network: one request per connection, so it stays simple
 * and light on a TV box. [handler] answers each request.
 */
class HttpServer(private val handler: (Request) -> Response) {
    private var socket: ServerSocket? = null
    private var pool: ExecutorService? = null

    var port = 0
        private set

    /** Tries [ports] in order and returns false when none of them is free. */
    fun start(ports: IntRange): Boolean {
        for (candidate in ports) {
            val server = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(candidate), 64) } }.getOrNull() ?: continue
            socket = server
            port = candidate
            val workers = Executors.newFixedThreadPool(WORKERS)
            pool = workers
            Thread({ accept(server, workers) }, "loop-http").start()
            return true
        }
        return false
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        pool?.shutdownNow()
        pool = null
    }

    private fun accept(server: ServerSocket, workers: ExecutorService) {
        while (!server.isClosed) {
            val client = try {
                server.accept()
            } catch (_: SocketException) {
                break
            }
            runCatching { workers.execute { serve(client) } }.onFailure { runCatching { client.close() } }
        }
    }

    private fun serve(client: Socket) {
        client.use { socket ->
            runCatching {
                socket.soTimeout = TIMEOUT_MS
                val input = BufferedInputStream(socket.getInputStream())
                val output = socket.getOutputStream().buffered(Response.BUFFER)
                val remote = (socket.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress
                val request = read(input, remote) ?: return
                val response = try {
                    handler(request)
                } catch (error: Throwable) {
                    Response.error(500, error.message ?: error.javaClass.simpleName)
                }
                // Whatever the handler did not read is dropped, so the answer goes out cleanly.
                runCatching { request.body.skip(Long.MAX_VALUE) }
                write(output, response, request.method == "HEAD")
            }
        }
    }

    private fun read(input: InputStream, remote: String?): Request? {
        val first = line(input) ?: return null
        val parts = first.split(' ')
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        while (true) {
            val header = line(input) ?: break
            if (header.isEmpty()) break
            val colon = header.indexOf(':')
            if (colon > 0) headers[header.substring(0, colon).trim().lowercase()] = header.substring(colon + 1).trim()
        }
        val target = parts[1]
        val path = decode(target.substringBefore('?'))
        val query = target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate {
            decode(it.substringBefore('=')) to decode(it.substringAfter('=', ""))
        }
        val length = headers["content-length"]?.toLongOrNull() ?: 0L
        return Request(parts[0].uppercase(), path, query, headers, Limited(input, length), length, remote)
    }

    private fun decode(text: String) = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)

    /** One header line; the request is read as bytes so a file in the body is not damaged. */
    private fun line(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (bytes.size() == 0) null else bytes.toString("ISO-8859-1")
            if (b == '\n'.code) break
            if (b != '\r'.code) bytes.write(b)
            if (bytes.size() > MAX_LINE) return null
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun write(out: OutputStream, response: Response, headOnly: Boolean) {
        val head = StringBuilder()
        head.append("HTTP/1.1 ").append(response.status).append(' ').append(reason(response.status)).append("\r\n")
        head.append("Content-Type: ").append(response.type).append("\r\n")
        if (response.length >= 0) head.append("Content-Length: ").append(response.length).append("\r\n")
        // Fonts and thumbnails may be kept by the browser; everything else changes all the time.
        if (response.headers.keys.none { it.equals("Cache-Control", true) }) head.append("Cache-Control: no-store\r\n")
        head.append("Connection: close\r\n")
        response.headers.forEach { (key, value) -> head.append(key).append(": ").append(value).append("\r\n") }
        head.append("\r\n")
        out.write(head.toString().toByteArray(Charsets.UTF_8))
        if (!headOnly) response.write(out)
        out.flush()
    }

    private fun reason(status: Int) = when (status) {
        200 -> "OK"
        206 -> "Partial Content"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        409 -> "Conflict"
        else -> if (status >= 500) "Server Error" else "Status"
    }

    /** The body of one request, so a handler can never read into the next bytes of the connection. */
    private class Limited(input: InputStream, private var left: Long) : FilterInputStream(input) {
        override fun read(): Int {
            if (left <= 0) return -1
            val b = super.read()
            if (b >= 0) left--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val read = super.read(b, off, minOf(len.toLong(), left).toInt())
            if (read > 0) left -= read
            return read
        }

        override fun skip(n: Long): Long {
            var skipped = 0L
            val buffer = ByteArray(8192)
            while (skipped < n && left > 0) {
                val read = read(buffer, 0, minOf(buffer.size.toLong(), n - skipped).toInt())
                if (read <= 0) break
                skipped += read
            }
            return skipped
        }

        override fun available(): Int = minOf(super.available().toLong(), left).toInt()
    }

    private companion object {
        const val WORKERS = 8
        const val TIMEOUT_MS = 60_000
        const val MAX_LINE = 16 * 1024
    }
}
