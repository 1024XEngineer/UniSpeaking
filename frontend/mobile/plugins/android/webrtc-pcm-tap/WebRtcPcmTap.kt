package com.unispeaking.mobile.audio

import android.content.Context
import android.media.AudioFormat
import com.oney.WebRTCModule.WebRTCModuleOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Captures a copy of the PCM frames already owned by WebRTC. This deliberately
 * avoids opening a second Android AudioRecord, which can steal or degrade the
 * VOICE_COMMUNICATION input used by realtime VAD and barge-in.
 */
object WebRtcPcmTap {
  private const val TARGET_SAMPLE_RATE = 16_000
  private const val SILENCE_FRAME_MS = 20
  private const val SILENCE_PADDING_MS = 200
  private const val SILENCE_RMS_THRESHOLD = 0.006

  private val lock = Any()
  private var recording = false
  private var finalized = false
  private var sampleRate = 0
  private var channelCount = 0
  private var audioFormat = 0
  private var segment = ByteArrayOutputStream()

  fun install(context: Context) {
    val audioDeviceModule = JavaAudioDeviceModule.builder(context)
      .setEnableVolumeLogger(false)
      .setSamplesReadyCallback { samples ->
        accept(
          samples.audioFormat,
          samples.channelCount,
          samples.sampleRate,
          samples.data,
        )
      }
      .createAudioDeviceModule()
    WebRTCModuleOptions.getInstance().audioDeviceModule = audioDeviceModule
  }

  fun startSegment() = synchronized(lock) {
    segment.reset()
    sampleRate = 0
    channelCount = 0
    audioFormat = 0
    recording = true
    finalized = false
  }

  fun stopSegment() = synchronized(lock) {
    recording = false
    finalized = segment.size() > 0
  }

  fun releaseSegment() = synchronized(lock) {
    recording = false
    finalized = false
    segment.reset()
    sampleRate = 0
    channelCount = 0
    audioFormat = 0
  }

  fun takeSegment(cacheDir: File): String? {
    val captured: ByteArray
    val sourceRate: Int
    val sourceChannels: Int
    val sourceFormat: Int
    synchronized(lock) {
      if (!finalized || segment.size() == 0) return null
      captured = segment.toByteArray()
      sourceRate = sampleRate
      sourceChannels = channelCount
      sourceFormat = audioFormat
      recording = false
      finalized = false
      segment.reset()
    }
    if (
      sourceFormat != AudioFormat.ENCODING_PCM_16BIT ||
      sourceRate <= 0 ||
      sourceChannels <= 0
    ) return null

    val mono = decodeMonoPcm16(captured, sourceChannels)
    if (mono.isEmpty()) return null
    val resampled = trimSilence(resample(mono, sourceRate))
    if (resampled.isEmpty()) return null
    val wav = encodeWav(resampled)
    val output = File.createTempFile("scene-turn-", ".wav", cacheDir)
    output.writeBytes(wav)
    return output.toURI().toString()
  }

  private fun accept(format: Int, channels: Int, rate: Int, data: ByteArray) {
    if (data.isEmpty()) return
    synchronized(lock) {
      if (!recording) return
      if (segment.size() == 0) {
        audioFormat = format
        channelCount = channels
        sampleRate = rate
      }
      if (format != audioFormat || channels != channelCount || rate != sampleRate) return
      segment.write(data)
    }
  }

  private fun decodeMonoPcm16(bytes: ByteArray, channels: Int): ShortArray {
    val sampleCount = bytes.size / 2
    val frameCount = sampleCount / channels
    val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val mono = ShortArray(frameCount)
    for (frame in 0 until frameCount) {
      var sum = 0
      for (channel in 0 until channels) sum += input.short.toInt()
      mono[frame] = (sum / channels)
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        .toShort()
    }
    return mono
  }

  private fun resample(input: ShortArray, sourceRate: Int): ShortArray {
    if (sourceRate == TARGET_SAMPLE_RATE) return input
    val outputLength = maxOf(
      1,
      kotlin.math.round(input.size.toDouble() * TARGET_SAMPLE_RATE / sourceRate).toInt(),
    )
    val output = ShortArray(outputLength)
    val ratio = sourceRate.toDouble() / TARGET_SAMPLE_RATE
    for (index in output.indices) {
      val sourcePosition = index * ratio
      val left = kotlin.math.floor(sourcePosition).toInt().coerceIn(0, input.lastIndex)
      val right = minOf(left + 1, input.lastIndex)
      val fraction = sourcePosition - left
      output[index] = kotlin.math.round(
        input[left] * (1.0 - fraction) + input[right] * fraction,
      )
        .toInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        .toShort()
    }
    return output
  }

  private fun trimSilence(input: ShortArray): ShortArray {
    val frameSize = TARGET_SAMPLE_RATE * SILENCE_FRAME_MS / 1_000
    val padding = TARGET_SAMPLE_RATE * SILENCE_PADDING_MS / 1_000
    var firstActive = -1
    var lastActive = -1
    var offset = 0
    while (offset < input.size) {
      val end = minOf(offset + frameSize, input.size)
      var squareSum = 0.0
      for (index in offset until end) {
        val normalized = input[index].toDouble() / Short.MAX_VALUE
        squareSum += normalized * normalized
      }
      val rms = kotlin.math.sqrt(squareSum / (end - offset))
      if (rms >= SILENCE_RMS_THRESHOLD) {
        if (firstActive < 0) firstActive = offset
        lastActive = end
      }
      offset = end
    }
    if (firstActive < 0) return input
    return input.copyOfRange(
      maxOf(0, firstActive - padding),
      minOf(input.size, lastActive + padding),
    )
  }

  private fun encodeWav(samples: ShortArray): ByteArray {
    val dataLength = samples.size * 2
    val output = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN)
    output.put("RIFF".toByteArray(Charsets.US_ASCII))
    output.putInt(36 + dataLength)
    output.put("WAVE".toByteArray(Charsets.US_ASCII))
    output.put("fmt ".toByteArray(Charsets.US_ASCII))
    output.putInt(16)
    output.putShort(1)
    output.putShort(1)
    output.putInt(TARGET_SAMPLE_RATE)
    output.putInt(TARGET_SAMPLE_RATE * 2)
    output.putShort(2)
    output.putShort(16)
    output.put("data".toByteArray(Charsets.US_ASCII))
    output.putInt(dataLength)
    samples.forEach(output::putShort)
    return output.array()
  }
}
