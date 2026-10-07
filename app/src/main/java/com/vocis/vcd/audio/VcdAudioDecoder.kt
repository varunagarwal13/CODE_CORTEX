package com.vocis.vcd.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.vocis.vcd.domain.VcdConstants
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Universal Audio Decoder for VOCIS Voice Clone Defence.
 *
 * Decodes any audio file (WAV, OGG, MPEG, MP3, AAC) into standard 16 kHz mono float32 samples [-1.0, 1.0],
 * exactly matching the shape consumed by Resemblyzer and AASIST ONNX models.
 */
object VcdAudioDecoder {

    private const val TAG = "VcdAudioDecoder"
    private const val TIMEOUT_US = 10_000L

    data class DecodedAudio(
        val samples: FloatArray,
        val sourceSampleRate: Int,
        val sourceChannels: Int,
        val durationSeconds: Float
    )

    /**
     * Decodes an audio file directly from app assets.
     */
    fun decodeAsset(context: Context, assetPath: String): DecodedAudio {
        // Fast path for WAV assets
        try {
            context.assets.open(assetPath).use { stream ->
                val wav = decodeWavStream(stream)
                if (wav != null) return wav
            }
        } catch (e: Exception) {
            Log.d(TAG, "WAV fast path skipped for asset $assetPath: ${e.message}")
        }

        // Fallback: Copy to temporary cache file and decode via MediaExtractor
        val tempFile = File(context.cacheDir, "vcd_temp_${System.currentTimeMillis()}.tmp")
        try {
            context.assets.open(assetPath).use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            return decodeFile(tempFile)
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    /**
     * Decodes audio from an Android Content or File Uri.
     */
    fun decodeUri(context: Context, uri: Uri): DecodedAudio {
        // Try WAV fast path first
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val wav = decodeWavStream(stream)
                if (wav != null) return wav
            }
        } catch (e: Exception) {
            Log.d(TAG, "WAV fast path skipped for URI: ${e.message}")
        }

        val extractor = MediaExtractor()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: error("Unable to open file descriptor for: $uri")

            return decodeWithExtractor(extractor)
        } finally {
            runCatching { extractor.release() }
        }
    }

    /**
     * Decodes an audio file from filesystem.
     */
    fun decodeFile(file: File): DecodedAudio {
        file.inputStream().use { stream ->
            val wav = decodeWavStream(stream)
            if (wav != null) return wav
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            return decodeWithExtractor(extractor)
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun decodeWavStream(stream: InputStream): DecodedAudio? {
        val bytes = stream.readBytes()
        if (bytes.size < 44) return null

        // Check RIFF and WAVE magic headers
        if (bytes[0].toInt() != 'R'.code || bytes[1].toInt() != 'I'.code ||
            bytes[2].toInt() != 'F'.code || bytes[3].toInt() != 'F'.code ||
            bytes[8].toInt() != 'W'.code || bytes[9].toInt() != 'A'.code ||
            bytes[10].toInt() != 'V'.code || bytes[11].toInt() != 'E'.code
        ) {
            return null
        }

        var offset = 12
        var channels = 1
        var sampleRate = 16000
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val nextOffset = offset + 8 + chunkSize

            if (chunkId == "fmt " && chunkSize >= 16) {
                val bb = ByteBuffer.wrap(bytes, offset + 8, chunkSize).order(ByteOrder.LITTLE_ENDIAN)
                val format = bb.short.toInt() // 1 = PCM
                channels = bb.short.toInt()
                sampleRate = bb.int
                bb.int // byteRate
                bb.short // blockAlign
                bitsPerSample = bb.short.toInt()
            } else if (chunkId == "data") {
                dataOffset = offset + 8
                dataSize = chunkSize
                break
            }

            offset = nextOffset
        }

        if (dataOffset == -1 || dataOffset + dataSize > bytes.size) {
            // If data chunk header was truncated, take remainder of bytes
            dataOffset = 44
            dataSize = bytes.size - 44
        }

        if (bitsPerSample != 16) {
            return null // Fallback to MediaExtractor for non-16-bit PCM
        }

        val numSamples = dataSize / (2 * channels)
        val monoPcm = FloatArray(numSamples)
        val bb = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until numSamples) {
            var sum = 0f
            for (c in 0 until channels) {
                sum += bb.short / 32768.0f
            }
            monoPcm[i] = sum / channels
        }

        val resampled = if (sampleRate != VcdConstants.SAMPLE_RATE) {
            SincResampler.resample(monoPcm, sampleRate, VcdConstants.SAMPLE_RATE)
        } else {
            monoPcm
        }

        val duration = resampled.size.toFloat() / VcdConstants.SAMPLE_RATE
        return DecodedAudio(resampled, sampleRate, channels, duration)
    }

    private fun decodeWithExtractor(extractor: MediaExtractor): DecodedAudio {
        val trackCount = extractor.trackCount
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        var mime: String? = null

        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val trackMime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (trackMime.startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = format
                mime = trackMime
                break
            }
        }

        if (audioTrackIndex == -1 || audioFormat == null || mime == null) {
            error("No audio track found in media file")
        }

        extractor.selectTrack(audioTrackIndex)
        val sourceRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else 16000

        val sourceChannels = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else 1

        val codec = MediaCodec.createDecoderByType(mime)
        val outStream = ByteArrayOutputStream(1 shl 20)
        try {
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            var isOutputFloat = false

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = codec.outputFormat
                        isOutputFloat = outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                                outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outIndex >= 0) {
                        val buffer = codec.getOutputBuffer(outIndex)!!
                        if (info.size > 0) {
                            val chunk = ByteArray(info.size)
                            buffer.position(info.offset)
                            buffer.get(chunk, 0, info.size)
                            val pcmBytes = if (isOutputFloat) floatPcmToShortPcm(chunk) else chunk
                            outStream.write(pcmBytes)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }

        val rawPcm = outStream.toByteArray()
        val numSamples = rawPcm.size / (2 * sourceChannels)
        val mono = FloatArray(numSamples)
        val bb = ByteBuffer.wrap(rawPcm).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until numSamples) {
            var sum = 0f
            for (c in 0 until sourceChannels) {
                sum += bb.short / 32768.0f
            }
            mono[i] = sum / sourceChannels
        }

        val resampled = if (sourceRate != VcdConstants.SAMPLE_RATE) {
            SincResampler.resample(mono, sourceRate, VcdConstants.SAMPLE_RATE)
        } else {
            mono
        }

        val duration = resampled.size.toFloat() / VcdConstants.SAMPLE_RATE
        return DecodedAudio(resampled, sourceRate, sourceChannels, duration)
    }

    private fun floatPcmToShortPcm(src: ByteArray): ByteArray {
        val bb = ByteBuffer.wrap(src).order(ByteOrder.nativeOrder())
        val count = src.size / 4
        val out = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(count) {
            val v = (bb.float.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            out.putShort(v)
        }
        return out.array()
    }
}
