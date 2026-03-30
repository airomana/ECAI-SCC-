package com.eldercare.backend.controller;

import com.eldercare.backend.common.ApiResponse;
import com.eldercare.backend.service.FamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/family")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;

    /** 子女端：通过邀请码发起绑定申请 */
    @PostMapping("/link")
    public ApiResponse<?> requestLink(
            @AuthenticationPrincipal Long userId,
            @RequestBody Map<String, String> body) {
        try {
            familyService.requestLink(userId, body.get("inviteCode"));
            return ApiResponse.success();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 父母端：查看待处理的绑定申请 */
    @GetMapping("/requests")
    public ApiResponse<?> getPendingRequests(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(familyService.getPendingRequests(userId));
    }

    /** 父母端：审批绑定申请 */
    @PostMapping("/requests/{requestId}/handle")
    public ApiResponse<?> handleRequest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long requestId,
            @RequestBody Map<String, Boolean> body) {
        try {
            familyService.handleRequest(userId, requestId, body.get("approve"));
            return ApiResponse.success();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 获取家庭成员列表 */
    @GetMapping("/members")
    public ApiResponse<?> getMembers(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(familyService.getFamilyMembers(userId));
    }

    /** 子女端：查询自己的绑定申请状态 */
    @GetMapping("/my-requests")
    public ApiResponse<?> getMyRequests(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(familyService.getMyLinkRequests(userId));
    }
}
