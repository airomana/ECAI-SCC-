package com.eldercare.backend.controller;

import com.eldercare.backend.common.ApiResponse;
import com.eldercare.backend.service.AliyunAsrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语音识别控制器
 * 提供实时流式语音识别接口
 */
@RestController
@RequestMapping("/api/asr")
public class AsrController {
    
    private static final Logger log = LoggerFactory.getLogger(AsrController.class);
    
    private final AliyunAsrService asrService;
    
    // SSE连接管理
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    
    public AsrController(AliyunAsrService asrService) {
        this.asrService = asrService;
    }
    
    /**
     * 创建实时语音识别会话
     * 返回SSE连接，用于接收识别结果
     * 
     * @return SSE emitter
     */
    @GetMapping(value = "/realtime", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter createRealtimeSession() {
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        // 创建SSE连接，超时5分钟
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        
        emitter.onCompletion(() -> {
            log.info("SSE completed for session: {}", sessionId);
            emitters.remove(sessionId);
            asrService.closeSession(sessionId);
        });
        
        emitter.onTimeout(() -> {
            log.info("SSE timeout for session: {}", sessionId);
            emitters.remove(sessionId);
            asrService.closeSession(sessionId);
        });
        
        emitter.onError(e -> {
            log.error("SSE error for session: {}", sessionId, e);
            emitters.remove(sessionId);
            asrService.closeSession(sessionId);
        });
        
        emitters.put(sessionId, emitter);
        
        // 创建ASR会话
        boolean created = asrService.createSession(sessionId, emitter);
        if (!created) {
            emitter.completeWithError(new RuntimeException("Failed to create ASR session"));
            emitters.remove(sessionId);
            return emitter;
        }
        
        // 发送会话ID给客户端
        try {
            emitter.send(SseEmitter.event()
                    .name("session")
                    .data(sessionId));
        } catch (Exception e) {
            log.error("Failed to send session ID", e);
        }
        
        log.info("Created realtime ASR session: {}", sessionId);
        return emitter;
    }
    
    /**
     * 发送原始 PCM 音频数据，避免 Base64/JSON 带来的额外开销。
     */
    @PostMapping(value = "/audio/raw", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ApiResponse<Void>> sendAudioRaw(
            @RequestParam("sessionId") String sessionId,
            @RequestBody byte[] audioBytes
    ) {
        if (sessionId == null || sessionId.isBlank() || audioBytes == null || audioBytes.length == 0) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Missing sessionId or audio bytes"));
        }

        boolean success = asrService.sendAudioBytes(sessionId, audioBytes);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success(null));
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Session not found or not ready"));
        }
    }
    
    /**
     * 结束识别会话
     * 
     * @param body 包含sessionId
     * @return 操作结果
     */
    @PostMapping("/finish")
    public ResponseEntity<ApiResponse<Void>> finishSession(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        
        if (sessionId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Missing sessionId"));
        }
        
        asrService.finishSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    /**
     * 关闭识别会话
     * 
     * @param body 包含sessionId
     * @return 操作结果
     */
    @PostMapping("/close")
    public ResponseEntity<ApiResponse<Void>> closeSession(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        
        if (sessionId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Missing sessionId"));
        }
        
        asrService.closeSession(sessionId);
        emitters.remove(sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
