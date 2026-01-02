# TinyRtspKt 🎥

**A lightweight, zero-dependency RTSP Server implementation for Android with native H.265 (HEVC) support.**

[![](https://jitpack.io/v/cagedbird043/TinyRtspKt.svg)](https://jitpack.io/#cagedbird043/TinyRtspKt)

## Why this exists?

Most Android RTSP libraries are either:
1.  **Too heavy**: Dependent on ffmpeg or massive frameworks.
2.  **Too old**: Lack proper H.265 (HEVC) RTP packetization (RFC 7798).
3.  **Too complex**: Hard to modify or integrate for simple use cases.

**TinyRtspKt** is written in pure Kotlin (< 500 lines), supports RFC 7798 (Type 49 Fragmentation Units), and fixes common issues like "Illegal Temporal ID" found in older libraries.

## Features

*   ✅ **Zero Dependencies**: Pure Kotlin/Java standard library.
*   ✅ **H.265 (HEVC) Support**: Correct Type 49 FU packetization.
*   ✅ **H.264 (AVC) Support**: Standard Type 28 FU packetization.
*   ✅ **Low Latency**: Optimized for UDP streaming.

## Installation

Add it in your root build.gradle at the end of repositories:

```gradle
dependencyResolutionManagement {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

Add the dependency:

```gradle
dependencies {
    implementation 'com.github.cagedbird043:TinyRtspKt:1.0.0'
}
```

## Usage

```kotlin
// 1. Create Server
val server = TinyRtspServer(8554) { session ->
    session.isHevc = true // or false for H.264
    // session.sps = ...
    // session.pps = ...
    sessions.add(session)
}
server.start()

// 2. Feed NAL Units (from MediaCodec)
val packetizer = RtpPacketizer(isHevc = true)

fun onEncodedData(data: ByteArray, timestampUs: Long) {
    packetizer.packetize(data, timestampUs) { packet, size ->
        sessions.forEach { it.sendRtpPacket(packet) }
    }
}
```

## License

Apache 2.0
