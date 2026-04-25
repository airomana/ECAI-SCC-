package com.eldercare.ai.whisper

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 混合语音识别管理器
 * 优先使用云端阿里云ASR（流式识别），无网络时回退到本地Whisper
 *
 * 修复的问题：
 * 1. 竞态条件：sessionId 与 activeConnections 用 AtomicReference 同步
 * 2. 云端结果混乱：用 CompletableDeferred 绑定到具体会话，不依赖全局 SharedFlow
 * 3. 协程泄漏：processWithCloud 内部不再创建游离 CoroutineScope
 * 4. 网络检查误判：去掉 NET_CAPABILITY_VALIDATED
 * 5. 字节序：使用 ByteBuffer.LITTLE_ENDIAN 保证 PCM16 正确
 * 6. MediaType 废弃 API：改用扩展函数
 * 7. 缓冲区无上限：AudioRecorder 最多录 30 秒
 */
class HybridSpeechRecognizer private constructor(private val context: Context) {

    companion object {
        private const val TAG = "HybridASR"
        private const val MAX_RECORD_SECONDS = 30

        @Volatile
        private var INSTANCE: HybridSpeechRecognizer? = null

        fun getInstance(context: Context): HybridSpeechRecognizer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HybridSpeechRecognizer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // 依赖组件
    private val whisperProcessor = WhisperProcessor.getInstance(context)
    private val audioRecorder = AudioRecorder()
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // 后端API地址（从 ApiClient 读取，或由外部设置）
    private var backendUrl: String = "http://122.51.208.124:8080"

    // 当前云端会话状态（原子引用，避免竞态）
    private val cloudSession = AtomicReference<CloudSession?>(null)

    // error 事件在 session 分配前到达时的暂存
    private val pendingErrorBeforeSession = AtomicReference<String?>(null)

    // 识别结果流（仅用于 partial/segment 实时展示，final 通过 Deferred 传递）
    private val _partialResult = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val partialResult: SharedFlow<String> = _partialResult.asSharedFlow()

    // 是否正在识别
    @Volatile private var isRecognizing = false

    // ==================== 公开 API ====================

    fun setBackendUrl(url: String) {
        backendUrl = url.trimEnd('/')
    }

    /** 只检查 INTERNET 能力，不检查 VALIDATED（避免误判） */
    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isWhisperReady(): Boolean =
        whisperProcessor.isNativeLibraryLoaded() && whisperProcessor.isInitialized()

    suspend fun initWhisper(): Boolean = withContext(Dispatchers.IO) {
        if (whisperProcessor.isInitialized()) return@withContext true
        whisperProcessor.initFromAssets(context)
    }

    // VAD 触发后防止重复调用 stopRecognition
    @Volatile private var vadStopTriggered = false

    // VAD 触发时的外部回调（由 VoiceDiaryScreenV2 设置）
    var onVadSilence: (() -> Unit)? = null

    /**
     * 开始录音
     * @param useCloud 是否优先使用云端
     * @param onRms 实时音量回调（0-1）
     */
    fun startRecognition(useCloud: Boolean = true, onRms: ((Float) -> Unit)? = null): Boolean {
        if (isRecognizing) {
            Log.w(TAG, "Already recognizing")
            return false
        }

        val shouldUseCloud = useCloud && isNetworkAvailable()
        Log.i(TAG, "startRecognition useCloud=$shouldUseCloud (will connect when stopping)")
        vadStopTriggered = false

        audioRecorder.silenceTimeoutMs = 1500L
        audioRecorder.onSilenceDetected = {
            // 防止重复触发
            if (!vadStopTriggered) {
                vadStopTriggered = true
                Log.d(TAG, "VAD silence detected")
                // 通知 UI 层触发停止，由 UI 层统一调用 stopRecognition()，避免双重调用
                onVadSilence?.invoke()
            }
        }
        audioRecorder.onRmsChanged = { rms -> onRms?.invoke(rms) }

        val started = audioRecorder.startRecording()
        if (!started) return false

        isRecognizing = true
        // 不在这里建立云端连接，避免空闲超时
        return true
    }

    /**
     * 停止录音并返回识别结果
     * 云端失败时自动回退到本地 Whisper
     */
    suspend fun stopRecognition(): RecognitionResult {
        // 使用 synchronized 防止并发调用（VAD 和手动松开同时触发）
        synchronized(this) {
            if (!isRecognizing) {
                Log.w(TAG, "Not recognizing (already stopped)")
                return RecognitionResult.Error("未在录音")
            }
            isRecognizing = false
        }
        
        val audioData = audioRecorder.stopRecording()
        Log.d(TAG, "Recording stopped, samples=${audioData?.size ?: 0}")

        if (audioData == null || audioData.size < 1600) {
            return RecognitionResult.Error("录音太短，请重试")
        }

        // 限制最大长度（30秒）
        val trimmedAudio = if (audioData.size > MAX_RECORD_SECONDS * 16000)
            audioData.copyOf(MAX_RECORD_SECONDS * 16000) else audioData

        // 检查是否使用云端（在这里建立连接，避免空闲超时）
        val useCloud = isNetworkAvailable()
        return if (useCloud) {
            Log.i(TAG, "Using cloud ASR")
            // 建立连接并立即发送音频
            startCloudSession()
            val result = processWithCloud(trimmedAudio)
            closeCloudSession()
            result
        } else {
            Log.i(TAG, "Using local Whisper (no network)")
            processWithWhisper(trimmedAudio)
        }
    }

    private suspend fun processWithCloud(audioData: FloatArray): RecognitionResult {
        // 等待 sessionId 分配（最多 3 秒）
        val startWait = System.currentTimeMillis()
        while (cloudSession.get()?.sessionId == "pending" && System.currentTimeMillis() - startWait < 3000) {
            delay(50)
        }

        val session = cloudSession.get()
        val sid = session?.sessionId
        if (sid == null || sid == "pending") {
            Log.e(TAG, "Session ID not received, falling back to local")
            return processWithWhisper(audioData)
        }

        return try {
            // Float → PCM16 小端序
            val pcmBytes = floatArrayToPcm16Le(audioData)

            // 分块发送（每块 6400 字节 ≈ 200ms）
            val chunkSize = 6400
            var offset = 0
            while (offset < pcmBytes.size) {
                val end = minOf(offset + chunkSize, pcmBytes.size)
                val chunk = pcmBytes.copyOfRange(offset, end)
                sendAudioChunk(sid, Base64.getEncoder().encodeToString(chunk))
                offset = end
                // 快速发送，减少延迟
                delay(10)
            }

            // 通知服务端音频发送完毕
            finishCloudSession(sid)

            // 等待最终结果（最多 15 秒）
            val result = withTimeoutOrNull(15_000L) {
                session.resultDeferred.await()
            }

            result ?: RecognitionResult.Error("识别超时，请重试")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cloud ASR failed, falling back to local", e)
            processWithWhisper(audioData)
        }
    }

    fun cancelRecognition() {
        isRecognizing = false
        audioRecorder.forceStop()
        closeCloudSession()
    }

    fun release() {
        cancelRecognition()
        whisperProcessor.release()
    }

    // ==================== 云端识别 ====================

    /** 内部云端会话状态，包含 sessionId 和等待结果的 Deferred */
    private inner class CloudSession(
        val sessionId: String,
        val eventSource: EventSource,
        val resultDeferred: CompletableDeferred<RecognitionResult> = CompletableDeferred()
    )

    private fun startCloudSession() {
        pendingErrorBeforeSession.set(null)
        val request = Request.Builder()
            .url("$backendUrl/api/asr/realtime")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        val eventSourceFactory = EventSources.createFactory(okHttpClient)
        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "SSE opened")
            }

            // OkHttp SSE: onEvent(EventSource, String?, String, String)
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                val eventType = type ?: return
                Log.d(TAG, "SSE event type=$eventType data=${data.take(80)}")
                when (eventType) {
                    "session" -> {
                        // 服务端分配了 sessionId，更新 CloudSession
                        val existing = cloudSession.get()
                        if (existing != null && existing.sessionId == "pending") {
                            val newSession = CloudSession(data, existing.eventSource, existing.resultDeferred)
                            cloudSession.set(newSession)
                            Log.i(TAG, "Session ID assigned: $data")
                            // 如果在 session 之前已经收到 error，现在补完 deferred
                            val pendingError = pendingErrorBeforeSession.getAndSet(null)
                            if (pendingError != null) {
                                newSession.resultDeferred.complete(RecognitionResult.Error(pendingError))
                            }
                        }
                    }
                    "partial" -> _partialResult.tryEmit(data)
                    "segment" -> _partialResult.tryEmit(data)
                    "final"   -> {
                        cloudSession.get()?.resultDeferred?.complete(
                            if (data.isBlank()) RecognitionResult.Error("识别结果为空")
                            else RecognitionResult.Final(data)
                        )
                    }
                    "error"   -> {
                        val session = cloudSession.get()
                        if (session != null && session.sessionId != "pending") {
                            session.resultDeferred.complete(RecognitionResult.Error(data))
                        } else {
                            // session 还没分配，先暂存 error
                            pendingErrorBeforeSession.set(data)
                            Log.w(TAG, "Error received before session assigned, buffering: $data")
                        }
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d(TAG, "SSE closed")
                // 如果 Deferred 还未完成，说明异常关闭
                cloudSession.get()?.resultDeferred?.complete(RecognitionResult.Error("连接意外关闭"))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val msg = t?.message ?: response?.message ?: "未知错误"
                Log.e(TAG, "SSE failure: $msg", t)
                cloudSession.get()?.resultDeferred?.complete(RecognitionResult.Error("云端连接失败: $msg"))
            }
        }

        // 先用 "pending" 占位，等 session 事件到来后替换
        val eventSource = eventSourceFactory.newEventSource(request, listener)
        cloudSession.set(CloudSession("pending", eventSource))
    }

    private suspend fun processWithCloud(session: CloudSession, audioData: FloatArray): RecognitionResult {
        // 等待 sessionId 分配（最多 2 秒）
        val startWait = System.currentTimeMillis()
        while (cloudSession.get()?.sessionId == "pending" && System.currentTimeMillis() - startWait < 2000) {
            delay(50)
        }

        val sid = cloudSession.get()?.sessionId
        if (sid == null || sid == "pending") {
            Log.e(TAG, "Session ID not received, falling back to local")
            return processWithWhisper(audioData)
        }

        return try {
            // Float → PCM16 小端序
            val pcmBytes = floatArrayToPcm16Le(audioData)

            // 分块发送（每块 6400 字节 ≈ 200ms，减少网络往返次数）
            val chunkSize = 6400
            var offset = 0
            while (offset < pcmBytes.size) {
                val end = minOf(offset + chunkSize, pcmBytes.size)
                val chunk = pcmBytes.copyOfRange(offset, end)
                sendAudioChunk(sid, Base64.getEncoder().encodeToString(chunk))
                offset = end
                // 减少延迟到 20ms，加快发送速度
                delay(20)
            }

            // 通知服务端音频发送完毕
            finishCloudSession(sid)

            // 等待最终结果（减少到 10 秒）
            val result = withTimeoutOrNull(10_000L) {
                session.resultDeferred.await()
            }

            result ?: RecognitionResult.Error("识别超时，请重试")
        } catch (e: CancellationException) {
            throw e // 不要吞掉取消异常
        } catch (e: Exception) {
            Log.e(TAG, "Cloud ASR failed, falling back to local", e)
            processWithWhisper(audioData)
        }
    }

    private suspend fun sendAudioChunk(sessionId: String, audioBase64: String) {
        withContext(Dispatchers.IO) {
            try {
                // Decode base64 to raw bytes, as the backend now expects raw octet stream
                val audioBytes = Base64.getDecoder().decode(audioBase64)
                
                val body = audioBytes.toRequestBody("application/octet-stream".toMediaType())

                val request = Request.Builder()
                    .url("$backendUrl/api/asr/audio/raw?sessionId=$sessionId")
                    .post(body)
                    .build()

                okHttpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) Log.w(TAG, "sendAudio failed: ${resp.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendAudioChunk error", e)
            }
        }
    }

    private suspend fun finishCloudSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().put("sessionId", sessionId)
                    .toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("$backendUrl/api/asr/finish")
                    .post(body)
                    .build()

                okHttpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "finishCloudSession error", e)
            }
        }
    }

    private fun closeCloudSession() {
        val session = cloudSession.getAndSet(null) ?: return
        session.eventSource.cancel()

        // 通知服务端关闭（fire-and-forget，使用 okhttp 异步调用，不创建游离协程）
        try {
            val body = JSONObject().put("sessionId", session.sessionId)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$backendUrl/api/asr/close")
                .post(body)
                .build()

            okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.w(TAG, "closeCloudSession notify failed", e)
                }
                override fun onResponse(call: okhttp3.Call, response: Response) {
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "closeCloudSession error", e)
        }
    }

    // ==================== 本地识别 ====================

    private suspend fun processWithWhisper(audioData: FloatArray): RecognitionResult {
        return withContext(Dispatchers.IO) {
            try {
                val t0 = System.currentTimeMillis()
                val result = whisperProcessor.transcribe(audioData)
                Log.d(TAG, "Whisper done in ${System.currentTimeMillis() - t0}ms: '$result'")

                if (result.isNullOrBlank()) RecognitionResult.Error("未能识别到语音，请重试")
                else RecognitionResult.Final(result)
            } catch (e: Exception) {
                Log.e(TAG, "Whisper failed", e)
                RecognitionResult.Error("本地识别失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    // ==================== 工具方法 ====================

    /** Float[-1,1] → PCM16 小端序字节数组 */
    private fun floatArrayToPcm16Le(floats: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(floats.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) {
            buf.putShort((f * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        return buf.array()
    }

    // ==================== 结果类型 ====================

    sealed class RecognitionResult {
        data class Partial(val text: String) : RecognitionResult()
        data class Segment(val text: String) : RecognitionResult()
        data class Final(val text: String) : RecognitionResult()
        data class Error(val message: String) : RecognitionResult()

        /** 获取识别文本，Error 返回空字符串 */
        fun toText(): String = when (this) {
            is Partial -> text
            is Segment -> text
            is Final   -> text
            is Error   -> ""
        }

        val isFinal: Boolean get() = this is Final || this is Error
    }
}
