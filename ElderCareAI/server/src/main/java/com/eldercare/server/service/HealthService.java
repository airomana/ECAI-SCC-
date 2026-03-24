package com.eldercare.server.service;

import com.eldercare.server.entity.HealthProfile;
import com.eldercare.server.repository.HealthProfileRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class HealthService {
    private final HealthProfileRepository repository;

    public HealthService(HealthProfileRepository repository) {
        this.repository = repository;
    }

    public Optional<HealthProfile> findByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    public HealthProfile save(HealthProfile profile) {
        profile.setUpdatedAt(System.currentTimeMillis());
        return repository.save(profile);
    }
}
