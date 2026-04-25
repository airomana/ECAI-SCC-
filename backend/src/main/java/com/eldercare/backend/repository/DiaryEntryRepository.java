package com.eldercare.backend.repository;

import com.eldercare.backend.entity.DiaryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiaryEntryRepository extends JpaRepository<DiaryEntry, Long> {
    List<DiaryEntry> findByUserIdOrderByDateDesc(Long userId);
    List<DiaryEntry> findByUserIdAndDateBetweenOrderByDateDesc(Long userId, Long start, Long end);
}
