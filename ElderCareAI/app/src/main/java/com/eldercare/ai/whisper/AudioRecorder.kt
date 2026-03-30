package com.eldercare.ai.whisper

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * 音频录制器（改进版）
 * - 内置静音检测：RMS 低于阈值持续 [silenceTimeoutMs] 毫秒后触发 [onSilenceDetected]
 * - 实时 RMS 回调：[onRmsChanged]，用于 UI 波形显示
 * - 快速 getSnapshot()：直接返回当前缓冲区副本
 */
class AudioRecorder {

    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        /** 静音 RMS 阈值（0-1 归一化，约 -40dB） */
        private const val SILENCE_THRESHOLD = 0.01f

        /** 开始录音后多少毫秒内不触发静音检测（避免刚开始就停止） */
        private const val SILENCE_GUARD_MS = 800L
    }

    private var audioRecord: AudioRecord? = null
    @Volatile private var isRecording = false
    private val recordingState = MutableStateFlow(false)
    val isRecordingState: StateFlow<Boolean> = recordingState.asStateFlow()

    // 录音数据缓冲区
    private val recordedAudioData = mutableListOf<Short>()
    private var recordingThread: Thread? = null

    /** 静音超时（毫秒），默认 1000ms */
    var silenceTimeoutMs: Long = 1000L

    /** 静音检测触发回调（在录音线程调用，需自行切换到主线程） */
    var onSilenceDetected: (() -> Unit)? = null

    /** 实时 RMS 回调（0.0-1.0） */
    var onRmsChanged: ((Float) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf <= 0) {
            Log.e(TAG, "Invalid buffer size: $minBuf")
            return false
        }
        // 使用 2× 最小缓冲区，平衡延迟与稳定性
        val bufferSize = minBuf * 2

        return try {
            val ar = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize
            )
            if (ar.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord init failed")
                ar.release()
                return false
            }
            audioRecord = ar
            ar.startRecording()
            isRecording = true
            recordingState.value = true
            recordedAudioData.clear()

            recordingThread = Thread {
                readLoop(ar, bufferSize)
            }.apply {
                name = "AudioRecorder-Thread"
                isDaemon = true
                start()
            }
            Log.d(TAG, "Recording started, bufferSize=$bufferSize")
            true
        } catch (e: Exception) {
            Log.e(TAG, "startRecording failed", e)
            forceStop()
            false
        }
    }

    private fun readLoop(ar: AudioRecord, bufferSize: Int) {
        val buffer = ShortArray(bufferSize / 2) // 每次读半个缓冲区，降低延迟
        val startTime = System.currentTimeMillis()
        var silenceStartMs = -1L

        while (isRecording) {
            val read = ar.read(buffer, 0, buffer.size)
            if (read <= 0) {
                if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                    Log.w(TAG, "AudioRecord read error: $read")
                    break
                }
                Thread.sleep(5)
                continue
            }

            synchronized(recordedAudioData) {
                for (i in 0 until read) recordedAudioData.add(buffer[i])
            }

            // 计算 RMS
            val rms = computeRms(buffer, read)
            onRmsChanged?.invoke(rms)

            // 静音检测（录音开始后 SILENCE_GUARD_MS 才生效）
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > SILENCE_GUARD_MS) {
                if (rms < SILENCE_THRESHOLD) {
                    if (silenceStartMs < 0) silenceStartMs = System.currentTimeMillis()
                    val silenceDuration = System.currentTimeMillis() - silenceStartMs
                    if (silenceDuration >= silenceTimeoutMs) {
                        Log.d(TAG, "Silence detected for ${silenceDuration}ms, triggering auto-stop")
                        onSilenceDetected?.invoke()
                        break
                    }
                } else {
                    silenceStartMs = -1L // 有声音，重置静音计时
                }
            }
        }
        Log.d(TAG, "Read loop ended, total samples: ${recordedAudioData.size}")
    }

    private fun computeRms(buffer: ShortArray, count: Int): Float {
        if (count == 0) return 0f
        var sum = 0.0
        for (i in 0 until count) {
            val s = buffer[i] / 32768.0
            sum += s * s
        }
        return sqrt(sum / count).toFloat()
    }

    /**
     * 停止录制，返回归一化 Float 音频数据
     */
    fun stopRecording(): FloatArray? {
        isRecording = false
        recordingState.value = false

        val ar = audioRecord
        return try {
            ar?.stop()
            recordingThread?.join(600)
            recordingThread = null

            val data: List<Short>
            synchronized(recordedAudioData) {
                data = recordedAudioData.toList()
                recordedAudioData.clear()
            }
            ar?.release()
            audioRecord = null

            if (data.isEmpty()) {
                Log.w(TAG, "No audio data")
                return null
            }
            val floats = FloatArray(data.size) { i -> data[i] / 32768.0f }
            Log.d(TAG, "Stopped: ${floats.size} samples (${floats.size / SAMPLE_RATE.toFloat()}s)")
            floats
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording error", e)
            ar?.release()
            audioRecord = null
            null
        }
    }

    /**
     * 获取当前录音快照（不停止录音）
     */
    fun getSnapshot(): FloatArray? {
        val data: List<Short> = synchronized(recordedAudioData) {
            if (recordedAudioData.isEmpty()) return null
            recordedAudioData.toList()
        }
        return FloatArray(data.size) { i -> data[i] / 32768.0f }
    }

    /**
     * 强制停止，不返回数据
     */
    fun forceStop() {
        isRecording = false
        recordingState.value = false
        recordingThread?.interrupt()
        try { recordingThread?.join(200) } catch (_: Exception) {}
        recordingThread = null
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "forceStop error", e)
        }
        audioRecord = null
        synchronized(recordedAudioData) { recordedAudioData.clear() }
        Log.d(TAG, "Force stopped")
    }

    fun release() = forceStop()
}
