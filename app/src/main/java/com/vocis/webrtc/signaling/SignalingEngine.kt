package com.vocis.webrtc.signaling

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
enum class SignalingMessageType {
    INVITE,
    ANSWER,
    ICE_CANDIDATE,
    HANGUP,
    BUSY
}

@Serializable
data class SignalingMessage(
    val type: SignalingMessageType,
    val senderName: String,
    val sdp: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val candidate: String? = null,
    val reason: String? = null
)

/**
 * Line-delimited JSON signaling engine over raw TCP sockets on port 47821.
 */
class SignalingEngine(
    val port: Int = 47821,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var activeClientSocket: Socket? = null
    private var outWriter: PrintWriter? = null
    private val executor = Executors.newCachedThreadPool()

    var onMessageReceived: ((SignalingMessage) -> Unit)? = null
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null

    /**
     * Starts listening for incoming signaling connections.
     */
    fun startServer() {
        if (isRunning.getAndSet(true)) return

        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning.get()) {
                    val client = serverSocket?.accept() ?: break
                    handleIncomingSocket(client)
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Connects to a remote peer's signaling server.
     */
    fun connectToPeer(host: String, targetPort: Int = port, onConnected: (Boolean) -> Unit) {
        executor.execute {
            try {
                val socket = Socket(host, targetPort)
                handleIncomingSocket(socket)
                onConnected(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onConnected(false)
            }
        }
    }

    private fun handleIncomingSocket(socket: Socket) {
        activeClientSocket = socket
        outWriter = PrintWriter(socket.getOutputStream(), true)
        onConnectionStateChanged?.invoke(true)

        executor.execute {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (isRunning.get()) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) {
                        val message = json.decodeFromString<SignalingMessage>(line)
                        onMessageReceived?.invoke(message)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                onConnectionStateChanged?.invoke(false)
                closeActiveConnection()
            }
        }
    }

    /**
     * Sends a line-delimited JSON signaling message to connected peer.
     */
    fun sendMessage(message: SignalingMessage): Boolean {
        val writer = outWriter ?: return false
        return try {
            val jsonString = json.encodeToString(message)
            writer.println(jsonString)
            !writer.checkError()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun closeActiveConnection() {
        try {
            outWriter?.close()
            activeClientSocket?.close()
        } catch (_: Exception) {}
        outWriter = null
        activeClientSocket = null
    }

    fun stop() {
        isRunning.set(false)
        closeActiveConnection()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }
}
