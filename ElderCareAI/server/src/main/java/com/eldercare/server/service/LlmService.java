package com.eldercare.server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmService {

    @Value("${dashscope.api.key:default_api_key}")
    private String apiKey;

    private static final String API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
    private final RestTemplate restTemplate;

    public LlmService() {
        this.restTemplate = new RestTemplate();
    }

    public String generateWeeklyReport(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "qwen-turbo");
        
        Map<String, Object> input = new HashMap<>();
        input.put("messages", List.of(
            Map.of("role", "system", "content", "你是一个专业的健康管理助手，请根据提供的老人近7天饮食和情绪数据生成一份给子女查看的周报。"),
            Map.of("role", "user", "content", prompt)
        ));
        requestBody.put("input", input);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("result_format", "message");
        requestBody.put("parameters", parameters);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(API_URL, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("output")) {
                Map<String, Object> output = (Map<String, Object>) body.get("output");
                List<Map<String, Object>> choices = (List<Map<String, Object>>) output.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
            return "未能成功解析大模型响应：" + (body != null ? body.toString() : "响应为空");
        } catch (Exception e) {
            e.printStackTrace();
            return "生成周报失败，请检查 API Key 或网络: " + e.getMessage();
        }
    }
}
