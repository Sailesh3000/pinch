package com.expensetracker.extraction

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the ~547 MB model download survives interruption (Resume via HTTP
 * `Range`) and fails over to mirror URLs. Runs on the local JVM with a real
 * loopback HTTP server - no Android emulator/device required.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelDownloaderResumeTest {

    private lateinit var server: TinyHttpServer
    private lateinit var context: Context
    private val requests = mutableListOf<HttpRequest>()

    private val fullBody: ByteArray by lazy {
        ByteArray(PAYLOAD_BYTES) { (it % 251).toByte() }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        server = TinyHttpServer { request ->
            requests += request
            when (request.target) {
                "/missing" -> HttpResponse(404)
                "/model" -> modelResponse(request)
                else -> HttpResponse(404)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
        // Clean up files written into the test app's private dir.
        val dir = context.filesDir
        listOf(ModelDownloader.MODEL_FILENAME, "${ModelDownloader.MODEL_FILENAME}.tmp")
            .forEach { File(dir, it).delete() }
    }

    private fun modelUrl(path: String = "/model") =
        "http://127.0.0.1:${server.port}$path"

    private fun modelResponse(request: HttpRequest): HttpResponse {
        val range = request.headers["range"]
        val start = if (range != null) rangeStart(range) else 0
        val headers = mutableMapOf<String, String>()
        val body: ByteArray = if (start == 0) {
            headers["Content-Range"] = "bytes 0-${PAYLOAD_BYTES - 1}/$PAYLOAD_BYTES"
            fullBody
        } else {
            headers["Content-Range"] = "bytes $start-${PAYLOAD_BYTES - 1}/$PAYLOAD_BYTES"
            fullBody.copyOfRange(start, PAYLOAD_BYTES)
        }
        return HttpResponse(status = if (range != null) 206 else 200, headers = headers, body = body)
    }

    private fun rangeStart(range: String): Int {
        val start = range.substringAfter("bytes=").substringBefore("-").toIntOrNull() ?: 0
        return start.coerceIn(0, PAYLOAD_BYTES)
    }

    private fun writePartialTmp(bytes: Int) {
        val tmp = File(context.filesDir, "${ModelDownloader.MODEL_FILENAME}.tmp")
        tmp.writeBytes(fullBody.copyOfRange(0, bytes))
        assertTrue("partial temp file must exist for resume", tmp.exists())
    }

    @Test
    fun `resumes from partial temp file using Range header`() = runBlocking {
        val downloader = ModelDownloader(context, listOf(modelUrl()))
        writePartialTmp(PARTIAL_BYTES)

        val result = downloader.downloadIfNeeded()

        assertTrue("download should complete", result is ModelDownloader.DownloadState.Complete)
        val modelFile = File(context.filesDir, ModelDownloader.MODEL_FILENAME)
        assertTrue("model file should exist", modelFile.exists())
        assertEquals("model file should contain the whole payload", PAYLOAD_BYTES.toLong(), modelFile.length())
        assertTrue(
            "server must have received a byte-range resume request",
            requests.any { it.headers["range"] == "bytes=$PARTIAL_BYTES-" },
        )
        assertFalse(
            "no restart-from-scratch request should occur after a successful resume",
            requests.any { it.headers["range"] == null },
        )
    }

    @Test
    fun `falls back to the next mirror URL when the primary fails`() = runBlocking {
        val downloader = ModelDownloader(
            context,
            listOf(modelUrl("/missing"), modelUrl("/model")),
        )

        val result = downloader.downloadIfNeeded()

        assertTrue("fallback mirror should complete the download", result is ModelDownloader.DownloadState.Complete)
        val modelFile = File(context.filesDir, ModelDownloader.MODEL_FILENAME)
        assertNotNull("model path should be resolvable after fallback", downloader.modelPath)
        assertTrue("model file must exist after fallback download", modelFile.exists())
        assertEquals("model file should contain the whole payload", PAYLOAD_BYTES.toLong(), modelFile.length())
        assertTrue("primary mirror must have been attempted first", requests.any { it.target == "/missing" })
    }

    @Test
    fun `exposes primary mirror plus fallbacks in order`() {
        val urls = ModelDownloader(context).downloadUrls
        assertEquals(3, urls.size)
        assertTrue("primary must be HuggingFace", urls[0].startsWith("https://huggingface.co/"))
        assertTrue("second must be GitHub Releases", urls[1].contains("github.com"))
        assertEquals("no duplicated fallback URLs", urls.size, urls.toSet().size)
    }

    companion object {
        // Just above MIN_MODEL_SIZE_BYTES (50 MB) so the sanity check passes.
        private const val PAYLOAD_BYTES = 50_000_017
        private const val PARTIAL_BYTES = 10_000_000
    }
}

private data class HttpRequest(
    val target: String,
    val headers: Map<String, String>,
)

private data class HttpResponse(
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
)

/** Minimal single-connection-per-request HTTP/1.1 server for loopback tests. */
private class TinyHttpServer(
    private val handler: (HttpRequest) -> HttpResponse,
) {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val serverSocket = ServerSocket(0, 0, loopback)
    @Volatile private var running = false
    private var acceptThread: Thread? = null

    val port: Int get() = serverSocket.localPort

    fun start() {
        running = true
        acceptThread = thread("test-http-accept") {
            while (running) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: java.net.SocketException) {
                    break
                }
                thread("test-http-conn") {
                    try {
                        handle(socket)
                    } catch (_: Exception) {
                    } finally {
                        try {
                            socket.close()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        running = false
        try {
            serverSocket.close()
        } catch (_: Exception) {
        }
        acceptThread?.join(500)
    }

    private fun handle(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(" ")
        val target = parts.getOrNull(1) ?: return
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) break
            val idx = line.indexOf(':')
            if (idx > 0) {
                headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
        }

        val response = handler(HttpRequest(target, headers))
        val out = BufferedOutputStream(socket.getOutputStream())
        val statusText = when (response.status) {
            200 -> "OK"
            206 -> "Partial Content"
            404 -> "Not Found"
            else -> "OK"
        }
        out.write("HTTP/1.1 ${response.status} $statusText\r\n".toByteArray())
        for ((key, value) in response.headers) {
            out.write("$key: $value\r\n".toByteArray())
        }
        out.write("Content-Length: ${response.body.size}\r\n".toByteArray())
        out.write("Connection: close\r\n".toByteArray())
        out.write("\r\n".toByteArray())
        out.write(response.body)
        out.flush()
    }

    private fun thread(name: String, block: () -> Unit): Thread =
        Thread(block, name).apply { isDaemon = true; start() }
}