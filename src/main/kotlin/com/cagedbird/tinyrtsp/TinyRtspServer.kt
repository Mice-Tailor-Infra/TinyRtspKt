package com.cagedbird.tinyrtsp

import android.util.Log
import java.net.ServerSocket
import java.util.concurrent.Executors

/**
 * TinyRtspServer: A lightweight, zero-dependency RTSP Server for Android.
 * 
 * Usage:
 * val server = TinyRtspServer(8554) { session ->
 *     session.isHevc = true
 *     // Inject VPS/SPS/PPS here
 * }
 * server.start()
 */
class TinyRtspServer(
    private val port: Int,
    private val onSessionCreated: (RtspSession) -> Unit
) {
    private val TAG = "TinyRtspServer"
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                Log.i(TAG, "RTSP Server listening on port $port")
                while (running) {
                    val client = serverSocket?.accept() ?: break
                    val session = RtspSession(client)
                    onSessionCreated(session)
                    executor.execute(session)
                }
            } catch (e: Exception) {
                if (running) Log.e(TAG, "Server error", e)
            }
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (e: Exception) {}
        serverSocket = null
    }
}
