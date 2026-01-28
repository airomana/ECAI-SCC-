package com.eldercare.ai.whisper

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 音频录制器
 * 用于录制PCM音频数据，供Whisper识别使用
 */
class AudioRecorder {
    
    companion object {
        private const val TAG = "AudioRecorder"
        
        // Whisper要求的音频参数
        private const val SAMPLE_RATE = 16000  // 16kHz采样率
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO  // 单声道
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT  // 16位PCM
        private const val BUFFER_SIZE_MULTIPLIER = 2  // 缓冲区大小倍数
    }
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val recordingState = MutableStateFlow(false)
    
    // 用于存储录制的音频数据
    private val recordedAudioData = mutableListOf<Short>()
    private var recordingThread: Thread? = null
    
    val isRecordingState: StateFlow<Boolean> = recordingState.asStateFlow()
    
    /**
     * 开始录制（异步）
     * 需要在后台线程调用stopRecording()来获取录制的音频数据
     */
    fun startRecording(): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }
        
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ) * BUFFER_SIZE_MULTIPLIER
        
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size")
            return false
        }
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                audioRecord?.release()
                audioRecord = null
                return false
            }
            
            audioRecord?.startRecording()
            isRecording = true
            recordingState.value = true
            
            // 清空之前的数据
            recordedAudioData.clear()
            
            // 启动读取线程，持续读取音频数据
            recordingThread = Thread {
                val buffer = ShortArray(bufferSize)
                while (isRecording && audioRecord != null) {
                    try {
                        val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readSize > 0) {
                            synchronized(recordedAudioData) {
                                recordedAudioData.addAll(buffer.take(readSize))
                            }
                        } else if (readSize == AudioRecord.ERROR_INVALID_OPERATION || 
                                  readSize == AudioRecord.ERROR_BAD_VALUE) {
                            Log.w(TAG, "AudioRecord read error: $readSize")
                            break
                        } else if (readSize == 0) {
                            // 没有数据，短暂休眠后继续
                            Thread.sleep(10)
                        }
                    } catch (e: Exception) {
                        if (isRecording) {
                            Log.e(TAG, "Error reading audio data", e)
                        }
                        break
                    }
                }
                Log.d(TAG, "Recording thread stopped, total samples: ${recordedAudioData.size}")
            }.apply {
                name = "AudioRecorder-Thread"
                start()
            }
            
            Log.d(TAG, "Recording started, buffer size: $bufferSize")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            stopRecording()
            return false
        }
    }
    
    /**
     * 停止录制并获取音频数据
     * @return 录制的音频数据（Float数组，已归一化到-1.0到1.0）
     */
    fun stopRecording(): FloatArray? {
        if (!isRecording && recordedAudioData.isEmpty()) {
            Log.w(TAG, "Not recording and no data available")
            return null
        }
        
        // 先标记为停止，让读取线程退出
        isRecording = false
        recordingState.value = false
        
        val currentAudioRecord = audioRecord
        
        try {
            // 停止录音
            currentAudioRecord?.stop()
            Log.d(TAG, "AudioRecord stopped")
            
            // 等待读取线程完成（最多等待500ms）
            recordingThread?.join(500)
            recordingThread = null
            
            // 获取所有录制的数据
            val audioData: List<Short>
            synchronized(recordedAudioData) {
                audioData = recordedAudioData.toList()
                recordedAudioData.clear()
            }
            
            // 释放资源
            currentAudioRecord?.release()
            audioRecord = null
            
            if (audioData.isEmpty()) {
                Log.w(TAG, "No audio data captured")
                return null
            }
            
            // 转换为Float数组并归一化
            val floatArray = FloatArray(audioData.size) { i ->
                audioData[i] / 32768.0f
            }
            
            Log.d(TAG, "Recording stopped, captured ${floatArray.size} samples (${floatArray.size / SAMPLE_RATE.toFloat()} seconds)")
            return floatArray
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            try {
                currentAudioRecord?.stop()
                currentAudioRecord?.release()
            } catch (e2: Exception) {
                Log.e(TAG, "Error releasing AudioRecord", e2)
            }
            audioRecord = null
            recordingThread = null
            synchronized(recordedAudioData) {
                recordedAudioData.clear()
            }
            isRecording = false
            recordingState.value = false
            return null
        }
    }
    
    /**
     * 强制停止录音（不返回数据）
     */
    fun forceStop() {
        isRecording = false
        recordingState.value = false
        
        // 停止读取线程
        recordingThread?.interrupt()
        try {
            recordingThread?.join(100)
        } catch (e: Exception) {
            Log.w(TAG, "Error waiting for recording thread", e)
        }
        recordingThread = null
        
        if (audioRecord != null) {
            try {
                if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord?.stop()
                }
                audioRecord?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error force stopping", e)
            }
            audioRecord = null
        }
        
        synchronized(recordedAudioData) {
            recordedAudioData.clear()
        }
        
        Log.d(TAG, "Recording force stopped")
    }
    
    
    /**
     * 释放资源
     */
    fun release() {
        stopRecording()
    }
}
