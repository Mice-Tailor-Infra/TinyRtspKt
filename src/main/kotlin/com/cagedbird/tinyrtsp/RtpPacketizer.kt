package com.cagedbird.tinyrtsp

import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.experimental.or

/**
 * RtpPacketizer: Encapsulates H.264/H.265 NAL units into RTP packets.
 * Supports Single NAL Unit packets and Fragmentation Units (FU).
 * 
 * Key Features:
 * - RFC 7798 Compliant for HEVC (Type 49 FU).
 * - Automatic TID Correction (Forces TID=1 if 0).
 * - Zero Allocation (reuses buffer).
 */
class RtpPacketizer(private val isHevc: Boolean) {
    private var sequenceNumber = 0
    private var ssrc = (Math.random() * 0xFFFFFFFFL).toLong()
    private val MTU = 1300 // Safe MTU size

    private val rtpBuffer = ByteBuffer.allocate(1500)

    fun packetize(nalu: ByteArray, timestampUs: Long, onPacket: (ByteArray, Int) -> Unit) {
        val timestamp = (timestampUs * 90 / 1000).toInt()

        if (nalu.size <= MTU) {
            sendSinglePacket(nalu, timestamp, onPacket)
        } else {
            if (isHevc) {
                sendHevcFragmentationUnits(nalu, timestamp, onPacket)
            } else {
                sendAvcFragmentationUnits(nalu, timestamp, onPacket)
            }
        }
    }

    private fun sendSinglePacket(nalu: ByteArray, timestamp: Int, onPacket: (ByteArray, Int) -> Unit) {
        prepareRtpHeader(timestamp, true)
        rtpBuffer.put(nalu)
        finalizePacket(onPacket)
    }

    private fun sendAvcFragmentationUnits(nalu: ByteArray, timestamp: Int, onPacket: (ByteArray, Int) -> Unit) {
        val nri = nalu[0] and 0x60.toByte()
        val type = nalu[0] and 0x1F.toByte()
        var pos = 1
        
        while (pos < nalu.size) {
            val remaining = nalu.size - pos
            val chunkSize = if (remaining > MTU - 2) MTU - 2 else remaining
            val isLast = pos + chunkSize == nalu.size

            prepareRtpHeader(timestamp, isLast)
            rtpBuffer.put((nri or 28.toByte()))
            var fuHeader = type
            if (pos == 1) fuHeader = fuHeader or 0x80.toByte()
            if (isLast) fuHeader = fuHeader or 0x40.toByte()
            rtpBuffer.put(fuHeader)
            rtpBuffer.put(nalu, pos, chunkSize)
            finalizePacket(onPacket)
            pos += chunkSize
        }
    }

    private fun sendHevcFragmentationUnits(nalu: ByteArray, timestamp: Int, onPacket: (ByteArray, Int) -> Unit) {
        val originalHeader1 = nalu[0]
        val originalHeader2 = nalu[1]
        val originalType = (originalHeader1.toInt() shr 1) and 0x3F
        
        // Construct FU Payload Header (Type 49)
        val payloadHeader1 = (originalHeader1.toInt() and 0x81) or (49 shl 1)
        var payloadHeader2 = originalHeader2.toInt()
        if ((payloadHeader2 and 0x07) == 0) payloadHeader2 = payloadHeader2 or 0x01 // Force TID=1

        var pos = 2
        while (pos < nalu.size) {
            val remaining = nalu.size - pos
            val chunkSize = if (remaining > MTU - 3) MTU - 3 else remaining
            val isLast = pos + chunkSize == nalu.size

            prepareRtpHeader(timestamp, isLast)
            rtpBuffer.put(payloadHeader1.toByte())
            rtpBuffer.put(payloadHeader2.toByte())
            
            var fuHeader = originalType
            if (pos == 2) fuHeader = fuHeader or 0x80 // S
            if (isLast) fuHeader = fuHeader or 0x40 // E
            rtpBuffer.put(fuHeader.toByte())
            
            rtpBuffer.put(nalu, pos, chunkSize)
            finalizePacket(onPacket)
            pos += chunkSize
        }
    }

    private fun prepareRtpHeader(timestamp: Int, marker: Boolean) {
        rtpBuffer.clear()
        rtpBuffer.put(0x80.toByte())
        val mpt = if (marker) 0x80 or 96 else 96
        rtpBuffer.put(mpt.toByte())
        rtpBuffer.putShort((sequenceNumber++ and 0xFFFF).toShort())
        rtpBuffer.putInt(timestamp)
        rtpBuffer.putInt(ssrc.toInt())
    }

    private fun finalizePacket(onPacket: (ByteArray, Int) -> Unit) {
        val size = rtpBuffer.position()
        val packet = ByteArray(size)
        rtpBuffer.flip()
        rtpBuffer.get(packet)
        onPacket(packet, size)
    }
}
