package com.eldercare.server.service;

import com.eldercare.server.entity.FridgeItem;
import com.eldercare.server.repository.FridgeItemRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FridgeService {
    private final FridgeItemRepository repository;

    public FridgeService(FridgeItemRepository repository) {
        this.repository = repository;
    }

    public List<FridgeItem> findByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    public FridgeItem save(FridgeItem item) {
        return repository.save(item);
    }
}
