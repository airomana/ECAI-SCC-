package com.eldercare.backend.controller;

import com.eldercare.backend.common.ApiResponse;
import com.eldercare.backend.entity.User;
import com.eldercare.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepo;

    /** 获取当前用户信息 */
    @GetMapping("/me")
    public ApiResponse<?> getMe(@AuthenticationPrincipal Long userId) {
        return userRepo.findById(userId)
                .map(u -> ApiResponse.success(toSafeMap(u)))
                .orElse(ApiResponse.error("用户不存在"));
    }

    /** 更新昵称 */
    @PatchMapping("/nickname")
    public ApiResponse<?> updateNickname(
            @AuthenticationPrincipal Long userId,
            @RequestBody Map<String, String> body) {
        return userRepo.findById(userId).map(u -> {
            u.setNickname(body.get("nickname"));
            userRepo.save(u);
            return ApiResponse.success();
        }).orElse(ApiResponse.error("用户不存在"));
    }

    /** 上传健康档案 JSON */
    @PutMapping("/profile")
    public ApiResponse<?> uploadProfile(
            @AuthenticationPrincipal Long userId,
            @RequestBody Map<String, String> body) {
        return userRepo.findById(userId).map(u -> {
            u.setProfileJson(body.get("profileJson"));
            userRepo.save(u);
            return ApiResponse.success();
        }).orElse(ApiResponse.error("用户不存在"));
    }

    /** 下载健康档案 JSON */
    @GetMapping("/profile")
    public ApiResponse<?> downloadProfile(@AuthenticationPrincipal Long userId) {
        return userRepo.findById(userId)
                .map(u -> ApiResponse.success(Map.of("profileJson", u.getProfileJson() != null ? u.getProfileJson() : "")))
                .orElse(ApiResponse.error("用户不存在"));
    }

    private Map<String, Object> toSafeMap(User u) {
        return Map.of(
                "id", u.getId(),
                "phone", u.getPhone(),
                "role", u.getRole(),
                "inviteCode", u.getInviteCode() != null ? u.getInviteCode() : "",
                "familyId", u.getFamilyId() != null ? u.getFamilyId() : "",
                "nickname", u.getNickname() != null ? u.getNickname() : "",
                "createdAt", u.getCreatedAt(),
                "lastLoginAt", u.getLastLoginAt()
        );
    }
}
