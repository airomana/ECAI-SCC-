package com.eldercare.ai.whisper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android原生语音识别器
 * 使用Android的SpeechRecognizer API进行语音识别
 * 这是一个临时方案，直到Whisper.cpp完全集成
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
    
    private val speechRecognizer: SpeechRecognizer? by lazy {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            Log.w(TAG, "Speech recognition not available on this device")
            null
        }
    }
    
    /**
     * 检查语音识别是否可用
     */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context) && speechRecognizer != null
    }
    
    /**
     * 开始识别（异步）
     * @return 识别结果文本，失败返回null
     */
    suspend fun recognize(): String? = suspendCancellableCoroutine { continuation ->
        if (speechRecognizer == null || !isAvailable()) {
            Log.e(TAG, "Speech recognizer not available")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        
        val recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
            }
            
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Beginning of speech")
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // 音量变化，可用于显示波形
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {
                // 音频缓冲区接收
            }
            
            override fun onEndOfSpeech() {
                Log.d(TAG, "End of speech")
            }
            
            override fun onError(error: Int) {
                val errorMessage = when (error) {
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
                Log.e(TAG, "Recognition error: $errorMessage")
                continuation.resume(null)
            }
            
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val result = matches?.firstOrNull()
                Log.d(TAG, "Recognition result: $result")
                continuation.resume(result)
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                // 部分结果，可用于实时显示
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.d(TAG, "Partial result: ${matches?.firstOrNull()}")
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {
                // 其他事件
            }
        }
        
        speechRecognizer?.setRecognitionListener(recognitionListener)
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")  // 中文
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        try {
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "Started listening")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            continuation.resume(null)
        }
        
        // 取消时停止识别
        continuation.invokeOnCancellation {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        }
    }
    
    /**
     * 停止识别
     */
    fun stop() {
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        speechRecognizer?.destroy()
    }
}
