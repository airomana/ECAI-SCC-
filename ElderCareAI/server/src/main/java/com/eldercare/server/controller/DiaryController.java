package com.eldercare.server.controller;

import com.eldercare.server.entity.DiaryEntry;
import com.eldercare.server.service.DiaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 语音日记控制器
 * 处理"今天吃了啥"功能的云端数据同步
 * 对应App端的 VoiceDiaryScreen.kt 和 SyncManager.kt
 */
@RestController
@RequestMapping("/api/diary")
public class DiaryController {
    private final DiaryService service;

    public DiaryController(DiaryService service) {
        this.service = service;
    }

    /**
     * 获取指定用户的日记列表
     * 子女端可通过此接口拉取父母的饮食记录
     * 
     * @param userId 目标用户ID（通常是父母的ID）
     * @param viewerId 调用者ID（用于权限校验，确保只有子女能查看父母数据）
     */
    @GetMapping
    public ResponseEntity<?> getDiaries(@RequestParam Long userId, @RequestParam(required = false) Long viewerId) {
        // TODO: 在此处添加权限校验逻辑
        // 1. 如果 viewerId == userId，说明是自己看自己，允许
        // 2. 如果 viewerId != userId，检查 FamilyRelation 表，确认 viewerId 是 userId 的子女
        
        List<DiaryEntry> entries = service.findByUserId(userId);
        return ResponseEntity.ok(entries);
    }

    /**
     * 创建/同步一条新的语音日记
     * 当老人在App录入日记后，SyncManager会自动调用此接口上传到云端
     */
    @PostMapping
    public DiaryEntry createDiary(@RequestBody DiaryEntry entry) {
        return service.save(entry);
    }
}
