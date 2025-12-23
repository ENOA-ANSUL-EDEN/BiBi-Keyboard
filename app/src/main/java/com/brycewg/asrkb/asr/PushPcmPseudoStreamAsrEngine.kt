package com.brycewg.asrkb.asr

import android.content.Context
import android.media.AudioFormat
import android.util.Log
import com.brycewg.asrkb.R
import com.brycewg.asrkb.store.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 推 PCM 的伪流式基础引擎：
 * - 外部（如小企鹅）通过 AIDL writePcm 持续推送 PCM；
 * - 引擎内部用 VAD 在停顿处分片，子类可对片段做离线识别并回调 onPartial 预览；
 * - finishPcm/stop() 时对整段音频做一次离线识别，回调 onFinal。
 *
 * 注意：该引擎仅做“分句预览”，不做自动判停（由外部 finishPcm 决定会话结束）。
 */
abstract class PushPcmPseudoStreamAsrEngine(
  protected val context: Context,
  protected val scope: CoroutineScope,
  protected val prefs: Prefs,
  protected val listener: StreamingAsrEngine.Listener,
  protected val onRequestDuration: ((Long) -> Unit)? = null
) : StreamingAsrEngine, ExternalPcmConsumer {

  companion object {
    private const val TAG = "PushPcmPseudoStream"
  }

  protected open val sampleRate: Int = 16000
  protected open val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO
  protected open val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT

  private val running = AtomicBoolean(false)
  private val finalized = AtomicBoolean(false)

  private val sessionBuffer = ByteArrayOutputStream()
  private val segmentBuffer = ByteArrayOutputStream()

  private var segVadDetector: VadDetector? = null

  override val isRunning: Boolean
    get() = running.get()

  protected open fun ensureReady(): Boolean = true

  protected abstract fun onSegmentBoundary(pcmSegment: ByteArray)

  protected abstract suspend fun onSessionFinished(fullPcm: ByteArray)

  override fun start() {
    if (running.get()) return
    if (!ensureReady()) return

    running.set(true)
    finalized.set(false)
    sessionBuffer.reset()
    segmentBuffer.reset()

    // 复用静音判停的窗口与灵敏度配置：分句窗口取停录窗口的 1/3（更短），便于中途快速预览
    val stopWindowMs = try {
      prefs.autoStopSilenceWindowMs
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to read silence window for push-pcm pseudo stream", t)
      1200
    }.coerceIn(300, 5000)
    val segmentWindowMs = (stopWindowMs / 3).coerceIn(300, stopWindowMs)

    segVadDetector = try {
      VadDetector(
        context = context,
        sampleRate = sampleRate,
        windowMs = segmentWindowMs,
        sensitivityLevel = prefs.autoStopSilenceSensitivity
      )
    } catch (t: Throwable) {
      Log.e(TAG, "Failed to create segment VAD for push-pcm pseudo stream", t)
      null
    }
  }

  override fun stop() {
    if (!running.get()) return
    running.set(false)
    try { listener.onStopped() } catch (t: Throwable) { Log.w(TAG, "notify onStopped failed", t) }
    finalizeOnce()
  }

  override fun appendPcm(pcm: ByteArray, sampleRate: Int, channels: Int) {
    if (!running.get()) return
    if (sampleRate != this.sampleRate || channels != 1) {
      Log.w(TAG, "ignore frame: sr=$sampleRate ch=$channels")
      return
    }
    if (pcm.isEmpty()) return

    try { listener.onAmplitude(calculateNormalizedAmplitude(pcm)) } catch (t: Throwable) {
      Log.w(TAG, "amp cb failed", t)
    }

    try {
      sessionBuffer.write(pcm)
      segmentBuffer.write(pcm)
    } catch (t: Throwable) {
      Log.e(TAG, "Failed to buffer audio chunk", t)
      return
    }

    val segVad = segVadDetector ?: return
    val cutSegment = try {
      segVad.shouldStop(pcm, pcm.size)
    } catch (t: Throwable) {
      Log.e(TAG, "Segment VAD shouldStop failed", t)
      false
    }
    if (cutSegment && segmentBuffer.size() > 0) {
      val segBytes = try { segmentBuffer.toByteArray() } catch (t: Throwable) {
        Log.e(TAG, "Failed to toByteArray for segment", t)
        null
      } ?: return
      segmentBuffer.reset()
      try { segVad.reset() } catch (t: Throwable) { Log.w(TAG, "Failed to reset segment VAD", t) }
      try {
        onSegmentBoundary(segBytes)
      } catch (t: Throwable) {
        Log.e(TAG, "onSegmentBoundary failed", t)
      }
    }
  }

  private fun finalizeOnce() {
    if (!finalized.compareAndSet(false, true)) return
    val fullPcm = try { sessionBuffer.toByteArray() } catch (t: Throwable) {
      Log.e(TAG, "Failed to dump session buffer", t)
      ByteArray(0)
    }
    sessionBuffer.reset()
    segmentBuffer.reset()

    val d = segVadDetector
    segVadDetector = null
    try { d?.release() } catch (t: Throwable) { Log.w(TAG, "Failed to release segment VAD", t) }

    if (fullPcm.isEmpty()) {
      try {
        listener.onError(context.getString(R.string.error_audio_empty))
      } catch (t: Throwable) {
        Log.w(TAG, "notify audio empty failed", t)
      }
      return
    }

    scope.launch(Dispatchers.IO) {
      try {
        val denoised = OfflineSpeechDenoiserManager.denoiseIfEnabled(
          context = context,
          prefs = prefs,
          pcm = fullPcm,
          sampleRate = sampleRate
        )
        onSessionFinished(denoised)
      } catch (t: Throwable) {
        if (t is CancellationException) {
          Log.d(TAG, "final recognition cancelled: ${t.message}")
        } else {
          Log.e(TAG, "Final recognition failed", t)
          try {
            listener.onError(
              context.getString(
                R.string.error_recognize_failed_with_reason,
                t.message ?: ""
              )
            )
          } catch (e: Throwable) {
            Log.w(TAG, "notify final error failed", e)
          }
        }
      }
    }
  }
}
