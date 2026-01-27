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
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 如果中文不支持，尝试英文
                    tts?.setLanguage(Locale.ENGLISH)
                }
                isInitialized = true
                processQueue()
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
        if (!isEnabled || text.isBlank()) return
        
        if (priority > 0) {
            // 高优先级，插入队列前面
            pendingQueue.add(0, text)
        } else {
            pendingQueue.add(text)
        }
        
        if (isInitialized && !isSpeaking) {
            processQueue()
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
        if (!isInitialized || isSpeaking || pendingQueue.isEmpty()) return
        
        val text = pendingQueue.removeAt(0)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
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