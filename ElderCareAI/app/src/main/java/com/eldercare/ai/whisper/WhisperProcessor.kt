package com.eldercare.ai.whisper

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Whisper语音识别处理器
 * 封装JNI调用，提供简单的Kotlin API
 */
class WhisperProcessor private constructor(context: Context) {
    
    companion object {
        private const val TAG = "WhisperProcessor"
        
        @Volatile
        private var INSTANCE: WhisperProcessor? = null
        
        fun getInstance(context: Context): WhisperProcessor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WhisperProcessor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private var isInitialized = false
    private var nativeLibraryLoaded = false
    
    init {
        // 加载native库
        try {
            System.loadLibrary("eldercare-ai")
            nativeLibraryLoaded = true
            Log.d(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            nativeLibraryLoaded = false
            Log.e(TAG, "Failed to load native library (this is OK if native code is not compiled yet)", e)
        } catch (e: Exception) {
            nativeLibraryLoaded = false
            Log.e(TAG, "Error loading native library", e)
        }
    }
    
    /**
     * 初始化Whisper模型
     * @param modelPath 模型文件路径（assets中的文件需要先复制到内部存储）
     */
    fun init(modelPath: String): Boolean {
        if (isInitialized) {
            Log.w(TAG, "Whisper already initialized")
            return true
        }
        
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file not found: $modelPath")
            return false
        }
        
        try {
            val result = nativeInit(modelPath)
            if (result) {
                isInitialized = true
                Log.d(TAG, "Whisper initialized successfully")
            } else {
                Log.e(TAG, "Whisper initialization failed")
            }
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Whisper", e)
            return false
        }
    }
    
    /**
     * 从assets复制模型文件到内部存储
     */
    fun initFromAssets(context: Context, assetFileName: String = "ggml-tiny-q8_0.bin"): Boolean {
        Log.d(TAG, "Initializing Whisper with model: $assetFileName")
        val modelDir = File(context.filesDir, "whisper")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        
        val modelFile = File(modelDir, assetFileName)
        
        // 如果文件已存在，直接使用
        if (modelFile.exists()) {
            val fileSizeMB = modelFile.length() / (1024.0 * 1024.0)
            Log.d(TAG, "Model file already exists: ${modelFile.absolutePath} (${String.format("%.2f", fileSizeMB)} MB)")
            Log.d(TAG, "Using model: $assetFileName")
            return init(modelFile.absolutePath)
        }
        
        // 从assets复制
        try {
            context.assets.open("whisper/$assetFileName").use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val fileSizeMB = modelFile.length() / (1024.0 * 1024.0)
            Log.d(TAG, "Model file copied to: ${modelFile.absolutePath} (${String.format("%.2f", fileSizeMB)} MB)")
            Log.d(TAG, "Using model: $assetFileName")
            return init(modelFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model file from assets: $assetFileName", e)
            return false
        }
    }
    
    /**
     * 语音转文字
     * @param audioData 音频数据（float数组，16kHz采样率，单声道）
     * @return 识别结果文本，失败返回null
     */
    fun transcribe(audioData: FloatArray): String? {
        // 如果native库未加载，直接返回null（让调用者使用fallback）
        if (!nativeLibraryLoaded) {
            Log.w(TAG, "Native library not loaded, cannot transcribe")
            return null
        }
        
        if (!isInitialized) {
            Log.e(TAG, "Whisper not initialized")
            return null
        }
        
        if (audioData.isEmpty()) {
            Log.w(TAG, "Audio data is empty")
            return null
        }
        
        // VAD：截断尾部静音，减少送入Whisper的音频长度
        val trimmed = trimSilence(audioData)
        val originalSec = audioData.size / 16000f
        val trimmedSec = trimmed.size / 16000f
        Log.d(TAG, "VAD trim: ${String.format("%.2f", originalSec)}s -> ${String.format("%.2f", trimmedSec)}s (${trimmed.size} samples)")
        
        if (trimmed.size < 1600) { // 少于0.1秒，不处理
            Log.w(TAG, "Audio too short after VAD trim")
            return null
        }
        
        try {
            val startMs = System.currentTimeMillis()
            val result = nativeTranscribe(trimmed)
            val elapsedMs = System.currentTimeMillis() - startMs
            Log.d(TAG, "Transcription completed in ${elapsedMs}ms (${String.format("%.2f", trimmedSec / (elapsedMs / 1000f))}x realtime), result length: ${result?.length ?: 0}")
            
            // 如果返回空字符串，也视为失败
            if (result.isNullOrBlank()) {
                Log.w(TAG, "Transcription returned empty or blank string")
                return null
            }
            
            return result
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native method not found, library may not be loaded", e)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription", e)
            return null
        }
    }

    /**
     * 简单VAD：截断尾部静音
     * 从末尾向前找最后一个能量超过阈值的位置，保留到该位置+0.3秒余量
     */
    private fun trimSilence(audio: FloatArray, silenceThreshold: Float = 0.01f, paddingSamples: Int = 4800): FloatArray {
        val windowSize = 1600 // 100ms窗口
        var lastActiveWindow = 0
        var i = 0
        while (i + windowSize <= audio.size) {
            var energy = 0f
            for (j in i until i + windowSize) energy += audio[j] * audio[j]
            energy /= windowSize
            if (energy > silenceThreshold * silenceThreshold) {
                lastActiveWindow = i + windowSize
            }
            i += windowSize
        }
        val endSample = minOf(lastActiveWindow + paddingSamples, audio.size)
        return if (endSample < audio.size * 0.9f) audio.copyOf(endSample) else audio
    }
    
    /**
     * 检查native库是否已加载
     */
    fun isNativeLibraryLoaded(): Boolean = nativeLibraryLoaded
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * 释放资源
     */
    fun release() {
        if (isInitialized) {
            nativeRelease()
            isInitialized = false
            Log.d(TAG, "Whisper resources released")
        }
    }
    
    // Native方法声明
    private external fun nativeInit(modelPath: String): Boolean
    private external fun nativeTranscribe(audioData: FloatArray): String?
    private external fun nativeRelease()
}
