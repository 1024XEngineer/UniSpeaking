package com.unispeaking.mobile.audio

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class WebRtcPcmTapModule(
  private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {
  override fun getName() = "WebRtcPcmTap"

  @ReactMethod
  fun startSegment(promise: Promise) {
    WebRtcPcmTap.startSegment()
    promise.resolve(null)
  }

  @ReactMethod
  fun stopSegment(promise: Promise) {
    WebRtcPcmTap.stopSegment()
    promise.resolve(null)
  }

  @ReactMethod
  fun takeSegment(promise: Promise) {
    try {
      promise.resolve(WebRtcPcmTap.takeSegment(reactContext.cacheDir))
    } catch (error: Exception) {
      promise.reject("WEBRTC_PCM_TAP_FAILED", "无法生成实时对话评分录音", error)
    }
  }

  @ReactMethod
  fun releaseSegment(promise: Promise) {
    WebRtcPcmTap.releaseSegment()
    promise.resolve(null)
  }
}
