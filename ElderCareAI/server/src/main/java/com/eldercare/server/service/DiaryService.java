package com.eldercare.server.service;

import com.eldercare.server.entity.DiaryEntry;
import com.eldercare.server.repository.DiaryEntryRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DiaryService {
    private final DiaryEntryRepository repository;

    public DiaryService(DiaryEntryRepository repository) {
        this.repository = repository;
    }

    public List<DiaryEntry> findByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    public DiaryEntry save(DiaryEntry entry) {
        return repository.save(entry);
    }
}
