package com.eldercare.server.controller;

import com.eldercare.server.entity.FridgeItem;
import com.eldercare.server.service.FridgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 冰箱食材控制器
 * 处理"拍冰箱"功能的云端数据同步
 * 对应App端的 FridgeScreen.kt 和 SyncManager.kt
 */
@RestController
@RequestMapping("/api/fridge")
public class FridgeController {
    private final FridgeService service;

    public FridgeController(FridgeService service) {
        this.service = service;
    }

    /**
     * 获取指定用户的冰箱食材列表
     * 子女端可远程查看父母冰箱里有什么
     *
     * @param userId 目标用户ID
     * @param viewerId 调用者ID（用于权限校验）
     */
    @GetMapping
    public ResponseEntity<?> getFridgeItems(@RequestParam Long userId, @RequestParam(required = false) Long viewerId) {
        // TODO: 权限校验逻辑同上
        List<FridgeItem> items = service.findByUserId(userId);
        return ResponseEntity.ok(items);
    }

    /**
     * 添加/同步冰箱食材
     * 当App识别出新食材时，会自动调用此接口上传
     */
    @PostMapping
    public FridgeItem addFridgeItem(@RequestBody FridgeItem item) {
        return service.save(item);
    }
}
