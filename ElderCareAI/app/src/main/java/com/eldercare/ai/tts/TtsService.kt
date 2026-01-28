package com.eldercare.ai.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.*

/**
 * TTS语音播报服务
 * 全局单例，支持队列和打断
 */
class TtsService private constructor(context: Context) {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isEnabled = true
    private val pendingQueue = mutableListOf<String>()
    private var isSpeaking = false
    
    init {
        android.util.Log.d("TtsService", "初始化TTS服务")
        tts = TextToSpeech(context) { status ->
            android.util.Log.d("TtsService", "TTS初始化回调，status=$status")
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINESE)
                android.util.Log.d("TtsService", "设置中文语言，result=$result")
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 如果中文不支持，尝试英文
                    android.util.Log.w("TtsService", "中文不支持，尝试英文")
                    tts?.setLanguage(Locale.ENGLISH)
                }
                isInitialized = true
                android.util.Log.d("TtsService", "TTS初始化完成，开始处理队列，队列大小: ${pendingQueue.size}")
                // 确保在初始化完成后立即处理队列
                if (pendingQueue.isNotEmpty()) {
                    processQueue()
                }
            } else {
                android.util.Log.e("TtsService", "TTS初始化失败，status=$status")
            }
        }
        
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }
            
            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                processQueue()
            }
            
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                processQueue()
            }
        })
    }
    
    /**
     * 设置是否启用TTS
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (!enabled) {
            stop()
        }
    }
    
    /**
     * 播报文本
     */
    fun speak(text: String, priority: Int = 0) {
        android.util.Log.d("TtsService", "speak called: text=$text, isEnabled=$isEnabled, isInitialized=$isInitialized, isSpeaking=$isSpeaking")
        if (!isEnabled || text.isBlank()) {
            android.util.Log.w("TtsService", "TTS未启用或文本为空，跳过播报")
            return
        }
        
        if (priority > 0) {
            // 高优先级，插入队列前面
            pendingQueue.add(0, text)
        } else {
            pendingQueue.add(text)
        }
        
        android.util.Log.d("TtsService", "队列大小: ${pendingQueue.size}")
        
        if (isInitialized && !isSpeaking) {
            processQueue()
        } else {
            android.util.Log.w("TtsService", "TTS未初始化或正在播报，等待初始化完成")
        }
    }
    
    /**
     * 停止播报
     */
    fun stop() {
        tts?.stop()
        pendingQueue.clear()
        isSpeaking = false
    }
    
    private fun processQueue() {
        android.util.Log.d("TtsService", "processQueue: isInitialized=$isInitialized, isSpeaking=$isSpeaking, queueSize=${pendingQueue.size}")
        if (!isInitialized || isSpeaking || pendingQueue.isEmpty()) {
            android.util.Log.d("TtsService", "processQueue跳过: isInitialized=$isInitialized, isSpeaking=$isSpeaking, isEmpty=${pendingQueue.isEmpty()}")
            return
        }
        
        val text = pendingQueue.removeAt(0)
        android.util.Log.d("TtsService", "开始播报: $text")
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
        android.util.Log.d("TtsService", "speak调用结果: $result")
    }
    
    /**
     * 释放资源
     */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
    
    companion object {
        @Volatile
        private var INSTANCE: TtsService? = null
        
        fun getInstance(context: Context): TtsService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TtsService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}