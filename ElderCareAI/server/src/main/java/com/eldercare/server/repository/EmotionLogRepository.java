package com.eldercare.server.repository;

import com.eldercare.server.entity.EmotionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmotionLogRepository extends JpaRepository<EmotionLog, Long> {
    List<EmotionLog> findByUserId(Long userId);

    @Query("SELECT e FROM EmotionLog e WHERE e.userId = :userId AND e.timestamp >= :startTime")
    List<EmotionLog> findByUserIdAndTimestampAfter(Long userId, Long startTime);
}
