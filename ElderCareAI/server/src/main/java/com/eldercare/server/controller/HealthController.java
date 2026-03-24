package com.eldercare.server.controller;

import com.eldercare.server.entity.HealthProfile;
import com.eldercare.server.service.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 健康档案控制器
 * 处理用户的健康信息（过敏、慢病等）同步
 * 对应App端的 SettingsScreen.kt 和 SyncManager.kt
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final HealthService service;

    public HealthController(HealthService service) {
        this.service = service;
    }

    /**
     * 获取指定用户的健康档案
     * 子女端可远程查看父母的健康状况
     *
     * @param userId 目标用户ID
     * @param viewerId 调用者ID（用于权限校验）
     */
    @GetMapping
    public ResponseEntity<HealthProfile> getHealthProfile(@RequestParam Long userId) {
        return service.findByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 更新健康档案
     * 当用户在App修改健康设置时，同步更新到云端
     */
    @PostMapping
    public HealthProfile updateHealthProfile(@RequestBody HealthProfile profile) {
        return service.save(profile);
    }
}
