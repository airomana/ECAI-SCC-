package com.eldercare.backend.controller;

import com.eldercare.backend.service.LlmProxyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * LLM 代理接口：App 调这里，服务端转发到 DashScope
 * API Key 只存服务端，不暴露给 App
 */
@Slf4j
@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

    private final LlmProxyService llmProxyService;

    /** 文本生成（对话、菜品描述、周报等） */
    @PostMapping(value = "/text", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> textGeneration(
            @AuthenticationPrincipal Long userId,
            @RequestBody Map<String, Object> requestBody) {
        try {
            log.debug("LLM text request from userId={}", userId);
            String result = llmProxyService.proxyTextGeneration(requestBody);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("LLM text proxy error", e);
            return ResponseEntity.internalServerError()
                    .body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /** 多模态生成（图片识别食材） */
    @PostMapping(value = "/multimodal", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> multimodalGeneration(
            @AuthenticationPrincipal Long userId,
            @RequestBody Map<String, Object> requestBody) {
        try {
            log.debug("LLM multimodal request from userId={}", userId);
            String result = llmProxyService.proxyMultimodalGeneration(requestBody);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("LLM multimodal proxy error", e);
            return ResponseEntity.internalServerError()
                    .body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
