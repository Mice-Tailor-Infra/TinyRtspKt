package com.cagedbird.tinyrtsp

import android.util.Base64
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.regex.Pattern

class RtspSession(private val socket: Socket) : Runnable {
    private val TAG = "RtspSession"
    @Volatile var running = true
    private var cSeq = "1"
    
    var isHevc = false
    var vps: ByteArray? = null
    var sps: ByteArray? = null
    var pps: ByteArray? = null
    
    private var clientRtpPort = 0
    private var clientAddress: InetAddress? = null
    @Volatile private var udpSocket: DatagramSocket? = null
    @Volatile private var playing = false

    override fun run() {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = socket.getOutputStream()
            clientAddress = socket.inetAddress

            while (running) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) continue
                
                val firstLine = line.split(" ")
                if (firstLine.size < 2) continue
                val method = firstLine[0]
                val headers = mutableMapOf<String, String>()
                
                var hLine = reader.readLine()
                while (hLine != null && hLine.isNotEmpty()) {
                    val part = hLine.split(": ", limit = 2)
                    if (part.size == 2) headers[part[0]] = part[1]
                    hLine = reader.readLine()
                }
                cSeq = headers["CSeq"] ?: "1"
                
                when (method) {
                    "OPTIONS" -> sendResponse(writer, "200 OK", "Public: OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN")
                    "DESCRIBE" -> {
                        val sdp = generateSdp()
                        sendResponse(writer, "200 OK", "Content-Type: application/sdp\r\nContent-Length: ${sdp.length}", sdp)
                    }
                    "SETUP" -> {
                        val transport = headers["Transport"] ?: ""
                        val matcher = Pattern.compile("client_port=(\\d+)-(\\d+)").matcher(transport)
                        if (matcher.find()) {
                            clientRtpPort = matcher.group(1).toInt()
                        }
                        udpSocket = DatagramSocket()
                        val serverPort = udpSocket!!.localPort
                        sendResponse(writer, "200 OK", "Transport: RTP/AVP/UDP;unicast;client_port=$clientRtpPort-${clientRtpPort+1};server_port=$serverPort-${serverPort+1};ssrc=12345678\r\nSession: 12345678")
                    }
                    "PLAY" -> {
                        sendResponse(writer, "200 OK", "Session: 12345678\r\nRange: npt=0.000-")
                        playing = true
                    }
                    "TEARDOWN" -> {
                        sendResponse(writer, "200 OK", "")
                        running = false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Session error", e)
        } finally {
            stop()
        }
    }

    private fun sendResponse(out: OutputStream, status: String, headers: String, body: String = "") {
        val response = "RTSP/1.0 $status\r\nCSeq: $cSeq\r\n$headers\r\n\r\n$body"
        out.write(response.toByteArray())
    }

    private fun generateSdp(): String {
        val ip = socket.localAddress.hostAddress
        val codecName = if (isHevc) "H265" else "H264"
        var fmtp = "a=fmtp:96 packetization-mode=1"
        if (isHevc) {
            val vpsStr = Base64.encodeToString(vps ?: ByteArray(0), Base64.NO_WRAP)
            val spsStr = Base64.encodeToString(sps ?: ByteArray(0), Base64.NO_WRAP)
            val ppsStr = Base64.encodeToString(pps ?: ByteArray(0), Base64.NO_WRAP)
            fmtp = "a=fmtp:96 sprop-vps=$vpsStr; sprop-sps=$spsStr; sprop-pps=$ppsStr"
        } else {
            val spsStr = Base64.encodeToString(sps ?: ByteArray(0), Base64.NO_WRAP)
            val ppsStr = Base64.encodeToString(pps ?: ByteArray(0), Base64.NO_WRAP)
            fmtp = "a=fmtp:96 packetization-mode=1;sprop-parameter-sets=$spsStr,$ppsStr"
        }
        return "v=0\r\no=- 0 0 IN IP4 $ip\r\ns=TinyRtspKt Stream\r\nc=IN IP4 $ip\r\nt=0 0\r\nm=video 0 RTP/AVP 96\r\na=rtpmap:96 $codecName/90000\r\n$fmtp\r\na=control:trackID=0\r\n"
    }

    fun sendRtpPacket(packet: ByteArray) {
        if (!playing || clientAddress == null || clientRtpPort == 0) return
        try {
            val socket = udpSocket
            if (socket != null) {
                socket.send(DatagramPacket(packet, packet.size, clientAddress, clientRtpPort))
            }
        } catch (e: Exception) { Log.e(TAG, "UDP Send Error", e) }
    }

    fun stop() {
        running = false
        try { socket.close() } catch (e: Exception) {}
        try { udpSocket?.close() } catch (e: Exception) {}
    }
}
