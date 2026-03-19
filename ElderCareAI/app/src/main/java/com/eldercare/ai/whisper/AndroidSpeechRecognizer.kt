package com.eldercare.ai.whisper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Android 原生语音识别器（Push-to-Talk 稳定版）
 *
 * 关键设计：
 * - 复用单个 SpeechRecognizer 实例，避免频繁 destroy/create 导致 BUSY
 * - 每次识别前先 cancel() 重置状态，等待 300ms 让服务稳定
 * - [isListening] 在 onReadyForSpeech 后才为 true
 * - [stopWhenReady] 供 Push-to-Talk 松开时调用，保证时序正确
 */
class AndroidSpeechRecognizer private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AndroidSpeechRecognizer"

        @Volatile
        private var INSTANCE: AndroidSpeechRecognizer? = null

        fun getInstance(context: Context): AndroidSpeechRecognizer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AndroidSpeechRecognizer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // 复用的识别器实例（主线程创建和使用）
    private var recognizer: SpeechRecognizer? = null

    // 识别器是否已就绪（onReadyForSpeech 触发后）
    @Volatile var isListening = false
        private set

    // 标记是否已请求停止（在 isListening 就绪前松开时设置）
    @Volatile private var stopRequested = false

    // 防止并发调用
    @Volatile private var isBusy = false

    /** 实时音量回调（0-1 归一化） */
    var onRmsChanged: ((Float) -> Unit)? = null

    /** 部分识别结果回调 */
    var onPartialResult: ((String) -> Unit)? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * 确保识别器实例存在（主线程调用）
     * 优先使用 Google 语音识别服务，避免华为系统服务被 HiTouch 占用
     */
    private fun ensureRecognizer() {
        if (recognizer == null) {
            // 尝试指定 Google 语音识别服务，绕过华为系统服务
            val googleSrPackage = "com.google.android.googlequicksearchbox"
            recognizer = try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    val pm = context.packageManager
                    val googleAvailable = try {
                        pm.getPackageInfo(googleSrPackage, 0)
                        true
                    } catch (_: Exception) { false }

                    if (googleAvailable) {
                        val componentName = android.content.ComponentName(
                            googleSrPackage,
                            "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
                        )
                        Log.d(TAG, "Using Google recognition service")
                        SpeechRecognizer.createSpeechRecognizer(context, componentName)
                    } else {
                        Log.d(TAG, "Google not available, using default")
                        SpeechRecognizer.createSpeechRecognizer(context)
                    }
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create with Google service, fallback to default", e)
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            Log.d(TAG, "SpeechRecognizer created")
        }
    }

    /**
     * 开始识别（Push-to-Talk 模式）
     * 必须在主线程调用（内部已切换）
     * 松开时调用 [stopWhenReady] 触发返回结果
     */
    suspend fun recognize(): String? = withContext(Dispatchers.Main) {
        if (isBusy) {
            Log.w(TAG, "recognize() called while busy, ignoring")
            return@withContext null
        }
        isBusy = true
        stopRequested = false
        isListening = false

        try {
            ensureRecognizer()
            // cancel 重置状态，等待服务稳定
            try { recognizer?.cancel() } catch (_: Exception) {}
            delay(300)

            // 如果 BUSY，最多重试 3 次，每次等待更长
            var result: String? = null
            var retryCount = 0
            while (retryCount < 3) {
                result = doRecognize(recognizer!!)
                if (result != null || lastError != SpeechRecognizer.ERROR_RECOGNIZER_BUSY) break
                retryCount++
                Log.w(TAG, "BUSY retry $retryCount/3, waiting ${retryCount * 500}ms")
                // 重建实例再试
                try { recognizer?.destroy() } catch (_: Exception) {}
                recognizer = null
                delay((retryCount * 500).toLong())
                ensureRecognizer()
            }
            result
        } finally {
            isListening = false
            isBusy = false
        }
    }

    // 记录最后一次错误码，用于重试判断
    @Volatile private var lastError: Int = -1

    private suspend fun doRecognize(sr: SpeechRecognizer): String? =
        suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)

            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                    isListening = true
                    // 如果用户在就绪前就松开了，立即停止
                    if (stopRequested) {
                        Log.d(TAG, "Stop was requested before ready, stopping now")
                        try { sr.stopListening() } catch (_: Exception) {}
                    }
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    onRmsChanged?.invoke(normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "End of speech")
                    isListening = false
                }

                override fun onError(error: Int) {
                    Log.e(TAG, "Recognition error: ${errorMessage(error)} (code=$error)")
                    lastError = error
                    isListening = false
                    if (finished.compareAndSet(false, true)) {
                        continuation.resume(null)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    Log.d(TAG, "Result: $text")
                    isListening = false
                    if (finished.compareAndSet(false, true)) {
                        continuation.resume(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        onPartialResult?.invoke(text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 100L)
            }

            try {
                sr.startListening(intent)
                Log.d(TAG, "startListening called")
            } catch (e: Exception) {
                Log.e(TAG, "startListening failed", e)
                isListening = false
                if (finished.compareAndSet(false, true)) continuation.resume(null)
            }

            continuation.invokeOnCancellation {
                isListening = false
                if (finished.compareAndSet(false, true)) {
                    try { sr.cancel() } catch (_: Exception) {}
                }
            }
        }

    /**
     * Push-to-Talk 松开时调用。
     * 若识别器已就绪（isListening=true），立即 stopListening()；
     * 否则标记 stopRequested，等 onReadyForSpeech 触发后自动停止。
     */
    fun stopWhenReady() {
        stopRequested = true
        if (isListening) {
            Log.d(TAG, "stopWhenReady: already listening, stopping now")
            try { recognizer?.stopListening() } catch (e: Exception) {
                Log.w(TAG, "stopListening error", e)
            }
        } else {
            Log.d(TAG, "stopWhenReady: not yet listening, will stop on onReadyForSpeech")
        }
    }

    /**
     * 立即取消识别（不返回结果）
     */
    fun cancel() {
        stopRequested = false
        isListening = false
        isBusy = false
        try { recognizer?.cancel() } catch (e: Exception) {
            Log.w(TAG, "cancel error", e)
        }
    }

    fun release() {
        cancel()
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "destroy error", e)
        }
        recognizer = null
    }

    private fun errorMessage(error: Int) = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "音频错误"
        SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
        SpeechRecognizer.ERROR_NETWORK -> "网络错误"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
        SpeechRecognizer.ERROR_NO_MATCH -> "无匹配结果"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙碌"
        SpeechRecognizer.ERROR_SERVER -> "服务器错误"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
        else -> "未知错误: $error"
    }
}
