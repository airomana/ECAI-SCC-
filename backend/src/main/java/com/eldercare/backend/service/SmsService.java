package com.eldercare.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private final StringRedisTemplate redis;

    @Value("${sms.test-mode:true}")
    private boolean testMode;

    @Value("${sms.expire-seconds:300}")
    private int expireSeconds;

    private static final String KEY_PREFIX = "sms:code:";

    public String sendCode(String phone) {
        String code = String.format("%06d", new SecureRandom().nextInt(1000000));
        redis.opsForValue().set(KEY_PREFIX + phone, code, Duration.ofSeconds(expireSeconds));
        log.info("[SMS TEST] phone={} code={}", phone, code);
        return code;
    }

    public boolean verifyCode(String phone, String code) {
        String stored = redis.opsForValue().get(KEY_PREFIX + phone);
        if (stored == null) return false;
        boolean match = stored.equals(code.trim());
        if (match) redis.delete(KEY_PREFIX + phone);
        return match;
    }
}
