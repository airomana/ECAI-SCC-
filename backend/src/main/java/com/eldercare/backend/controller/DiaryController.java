package com.eldercare.backend.controller;

import com.eldercare.backend.common.ApiResponse;
import com.eldercare.backend.dto.DiaryEntryDto;
import com.eldercare.backend.entity.EmotionLog;
import com.eldercare.backend.service.DiaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/diary")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    /** 父母端：批量上传日记（对话结束后同步） */
    @PostMapping("/sync")
    public ApiResponse<?> syncDiaries(
            @AuthenticationPrincipal Long userId,
            @RequestBody List<DiaryEntryDto> entries) {
        try {
            diaryService.syncDiaries(userId, entries);
            return ApiResponse.success();
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 父母端：上传情绪日志 */
    @PostMapping("/emotion-log")
    public ApiResponse<?> syncEmotionLog(
            @AuthenticationPrincipal Long userId,
            @RequestBody EmotionLog log) {
        try {
            diaryService.syncEmotionLog(userId, log);
            return ApiResponse.success();
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 子女端：获取父母日记 */
    @GetMapping("/parent")
    public ApiResponse<?> getParentDiaries(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long since) {
        try {
            return ApiResponse.success(diaryService.getParentDiaries(userId, since));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 子女端：获取父母情绪日志 */
    @GetMapping("/parent/emotion-logs")
    public ApiResponse<?> getParentEmotionLogs(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long since) {
        try {
            return ApiResponse.success(diaryService.getParentEmotionLogs(userId, since));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
