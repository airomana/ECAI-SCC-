package com.eldercare.server.service;

import com.eldercare.server.entity.EmotionLog;
import com.eldercare.server.repository.EmotionLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmotionLogService {
    private final EmotionLogRepository repository;

    public EmotionLogService(EmotionLogRepository repository) {
        this.repository = repository;
    }

    public EmotionLog save(EmotionLog log) {
        return repository.save(log);
    }

    public List<EmotionLog> findByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    public List<EmotionLog> getRecentLogs(Long userId, Long startTime) {
        return repository.findByUserIdAndTimestampAfter(userId, startTime);
    }
}
