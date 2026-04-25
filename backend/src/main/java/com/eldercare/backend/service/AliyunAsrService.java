package com.eldercare.backend.service;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 阿里云实时语音识别服务
 * 使用 DashScope paraformer-realtime-v2 模型进行流式语音识别
 *
 * 正确用法：
 *   recognizer.call(param, callback)  → 建立 WebSocket 连接
 *   recognizer.sendAudioFrame(ByteBuffer) → 分块发送 PCM 数据
 *   recognizer.stop()                 → 通知服务端结束，等待 final 结果
 *   recognizer.getDuplexApi().close() → 关闭 WebSocket
 */
@Service
public class AliyunAsrService {

    private static final Logger log = LoggerFactory.getLogger(AliyunAsrService.class);

    @Value("${llm.dashscope.api-key:}")
    private String apiKey;

    private static final String MODEL = "paraformer-realtime-v2";
    private static final int SAMPLE_RATE = 16000;
    private static final String AUDIO_FORMAT = "pcm";

    // 活跃会话管理
    private final ConcurrentHashMap<String, AsrSession> activeSessions = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * 创建实时语音识别会话
     */
    public boolean createSession(String sessionId, SseEmitter emitter) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("DashScope API Key not configured");
            sendSseEvent(emitter, "error", "API Key未配置");
            return false;
        }

        try {
            AsrSession session = new AsrSession(sessionId, emitter);
            boolean started = session.start();
            if (!started) {
                sendSseEvent(emitter, "error", "ASR会话启动失败");
                return false;
            }
            activeSessions.put(sessionId, session);
            log.info("ASR session created: {}", sessionId);
            return true;
        } catch (Exception e) {
            log.error("Failed to create ASR session: {}", sessionId, e);
            return false;
        }
    }

    /**
     * 发送音频数据到指定会话（Base64编码的PCM16LE数据）
     */
    public boolean sendAudioBytes(String sessionId, byte[] pcmBytes) {
        AsrSession session = activeSessions.get(sessionId);
        if (session == null) {
            log.warn("Session not found: {}", sessionId);
            return false;
        }

        try {
            session.appendAudio(pcmBytes);
            return true;
        } catch (Exception e) {
            log.error("Failed to send audio to session: {}", sessionId, e);
            return false;
        }
    }

    /**
     * 结束会话并触发最终识别
     */
    public void finishSession(String sessionId) {
        AsrSession session = activeSessions.get(sessionId);
        if (session != null) {
            try {
                session.finish();
            } catch (Exception e) {
                log.error("Failed to finish session: {}", sessionId, e);
            }
        }
    }

    /**
     * 关闭会话
     */
    public void closeSession(String sessionId) {
        AsrSession session = activeSessions.remove(sessionId);
        if (session != null) {
            session.close();
            log.info("ASR session closed: {}", sessionId);
        }
    }

    @PreDestroy
    public void cleanup() {
        activeSessions.values().forEach(AsrSession::close);
        activeSessions.clear();
        executorService.shutdown();
        log.info("All ASR sessions cleaned up");
    }

    private void sendSseEvent(SseEmitter emitter, String eventType, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventType).data(data));
        } catch (IOException e) {
            log.warn("Failed to send SSE event {}: {}", eventType, e.getMessage());
        }
    }

    /**
     * ASR会话封装
     *
     * 生命周期：
     *   start() → call(param, callback) 建立 WebSocket
     *   appendAudio() → sendAudioFrame() 分块发送
     *   finish() → stop() 通知结束，等待 onComplete
     *   close() → getDuplexApi().close() 释放连接
     */
    private class AsrSession {
        private final String sessionId;
        private final SseEmitter emitter;
        private final Recognition recognizer = new Recognition();
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final StringBuilder fullTranscript = new StringBuilder();
        private volatile String latestPartial = "";
        AsrSession(String sessionId, SseEmitter emitter) {
            this.sessionId = sessionId;
            this.emitter = emitter;
        }

        /**
         * 建立 WebSocket 连接（在后台线程中调用 call，它会阻塞直到 stop() 被调用）
         */
        boolean start() {
            try {
                // 设置北京地域 WebSocket URL
                Constants.baseWebsocketApiUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";

                RecognitionParam param = RecognitionParam.builder()
                        .model(MODEL)
                        .format(AUDIO_FORMAT)
                        .sampleRate(SAMPLE_RATE)
                        .apiKey(apiKey)
                        .parameter("language_hints", new String[]{"zh"})
                        .parameter("punctuation_prediction_enabled", true)
                        .parameter("inverse_text_normalization_enabled", true)
                        .parameter("semantic_punctuation_enabled", false)
                        .parameter("max_sentence_silence", 600)
                        .parameter("multi_threshold_mode_enabled", true)
                        .build();

                ResultCallback<RecognitionResult> callback = new ResultCallback<RecognitionResult>() {
                    @Override
                    public void onEvent(RecognitionResult result) {
                        if (result == null || result.getSentence() == null) return;
                        String text = result.getSentence().getText();
                        if (text == null || text.isBlank()) return;

                        if (result.isSentenceEnd()) {
                            appendFinalText(text);
                            latestPartial = "";
                            log.info("[{}] Segment: {}", sessionId, text);
                            sendSseEvent(emitter, "segment", text);
                        } else {
                            latestPartial = text;
                            log.debug("[{}] Partial: {}", sessionId, text);
                            sendSseEvent(emitter, "partial", text);
                        }
                    }

                    @Override
                    public void onComplete() {
                        if (!latestPartial.isBlank()) {
                            appendFinalText(latestPartial);
                            latestPartial = "";
                        }
                        log.info("[{}] Recognition complete, transcript: {}", sessionId, fullTranscript);
                        sendSseEvent(emitter, "final", fullTranscript.toString());
                    }

                    @Override
                    public void onError(Exception e) {
                        if (!closed.get()) {
                            log.error("[{}] Recognition error", sessionId, e);
                            sendSseEvent(emitter, "error", "识别失败: " + e.getMessage());
                        }
                    }
                };

                // call() 建立连接后立即返回（非阻塞），音频通过 sendAudioFrame 推送
                recognizer.call(param, callback);
                started.set(true);

                log.info("[{}] Recognition started", sessionId);
                return true;
            } catch (Exception e) {
                log.error("[{}] Failed to start recognition", sessionId, e);
                return false;
            }
        }

        void appendAudio(byte[] pcmBytes) {
            if (!started.get() || finished.get() || closed.get()) {
                log.warn("[{}] Cannot append audio: started={}, finished={}, closed={}",
                        sessionId, started.get(), finished.get(), closed.get());
                return;
            }
            try {
                ByteBuffer buffer = ByteBuffer.wrap(pcmBytes);
                recognizer.sendAudioFrame(buffer);
            } catch (Exception e) {
                log.error("[{}] Failed to send audio frame", sessionId, e);
            }
        }

        private void appendFinalText(String text) {
            if (text == null || text.isBlank()) return;
            if (fullTranscript.length() == 0) {
                fullTranscript.append(text);
                return;
            }
            if (!fullTranscript.toString().endsWith(text)) {
                fullTranscript.append(text);
            }
        }

        void finish() {
            if (!started.get() || !finished.compareAndSet(false, true)) return;
            executorService.submit(() -> {
                try {
                    recognizer.stop();
                    log.info("[{}] Recognition stopped", sessionId);
                } catch (Exception e) {
                    log.error("[{}] Error stopping recognition", sessionId, e);
                    sendSseEvent(emitter, "error", "停止识别失败: " + e.getMessage());
                }
            });
        }

        void close() {
            closed.set(true);
            // 如果还没 finish，先 stop
            if (started.get() && !finished.getAndSet(true)) {
                try {
                    recognizer.stop();
                } catch (Exception ignored) {}
            }
            try {
                if (recognizer.getDuplexApi() != null) {
                    recognizer.getDuplexApi().close(1000, "session closed");
                }
            } catch (Exception e) {
                log.warn("[{}] Error closing WebSocket", sessionId, e);
            }
        }
    }
}
