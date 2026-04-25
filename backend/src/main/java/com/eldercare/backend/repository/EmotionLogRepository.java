package com.eldercare.backend.repository;

import com.eldercare.backend.entity.EmotionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmotionLogRepository extends JpaRepository<EmotionLog, Long> {
    List<EmotionLog> findByUserIdOrderByDayTimestampDesc(Long userId);
    List<EmotionLog> findByUserIdAndDayTimestampBetween(Long userId, Long start, Long end);
    Optional<EmotionLog> findByUserIdAndDayTimestamp(Long userId, Long dayTimestamp);
}
