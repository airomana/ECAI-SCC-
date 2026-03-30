package com.eldercare.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * LLM 代理服务：转发 App 请求到 DashScope，API Key 只存服务端
 */
@Slf4j
@Service
public class LlmProxyService {

    @Value("${llm.dashscope.api-key}")
    private String apiKey;

    @Value("${llm.dashscope.base-url}")
    private String baseUrl;

    @Value("${llm.dashscope.timeout:30}")
    private int timeoutSeconds;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmProxyService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 转发文本生成请求
     * @param requestBody App 传来的请求体（JSON Map）
     * @return DashScope 返回的原始 JSON 字符串
     */
    public String proxyTextGeneration(Map<String, Object> requestBody) {
        return proxy("text-generation/generation", requestBody);
    }

    /**
     * 转发多模态请求（图片识别）
     */
    public String proxyMultimodalGeneration(Map<String, Object> requestBody) {
        return proxy("multimodal-generation/generation", requestBody);
    }

    private String proxy(String path, Map<String, Object> requestBody) {
        try {
            String json = objectMapper.writeValueAsString(requestBody);
            Request request = new Request.Builder()
                    .url(baseUrl + path)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .post(RequestBody.create(json, MediaType.parse("application/json; charset=utf-8")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "{}";
                if (!response.isSuccessful()) {
                    log.error("DashScope error: code={}, body={}", response.code(), body);
                    throw new RuntimeException("LLM API error: " + response.code() + " " + body);
                }
                return body;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM proxy error", e);
            throw new RuntimeException("LLM proxy failed: " + e.getMessage());
        }
    }
}
