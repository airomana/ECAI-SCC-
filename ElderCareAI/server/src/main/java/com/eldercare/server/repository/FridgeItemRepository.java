package com.eldercare.server.repository;

import com.eldercare.server.entity.FridgeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FridgeItemRepository extends JpaRepository<FridgeItem, Long> {
    List<FridgeItem> findByUserId(Long userId);
}
