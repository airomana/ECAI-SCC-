package com.eldercare.server.controller;

import com.eldercare.server.entity.User;
import com.eldercare.server.service.UserService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 用户认证控制器
 * 提供注册、登录、发送验证码的HTTP接口
 * 对应App端的 UserService.kt
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 发送手机验证码接口
     * 目前是模拟实现，实际生产环境需要对接短信服务商（如阿里云短信）
     * App调用：VerificationCodeService.sendCode()
     */
    @PostMapping("/send-code")
    public ResponseEntity<?> sendCode(@RequestBody PhoneRequest request) {
        // Mock implementation
        return ResponseEntity.ok("Code sent");
    }

    /**
     * 用户注册接口
     * 处理父母/子女角色的注册逻辑，包括邀请码校验
     * App调用：UserService.register()
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            User user = userService.register(request.getPhone(), request.getRole(), request.getInviteCode());
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 用户登录接口
     * 验证手机号是否存在，返回用户信息（包括家庭ID、角色等）
     * App调用：UserService.login()
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // Mock implementation: just check phone existence
        Optional<User> user = userService.findByPhone(request.getPhone());
        if (user.isPresent()) {
            return ResponseEntity.ok(user.get());
        } else {
            return ResponseEntity.badRequest().body("User not found");
        }
    }

    @PostMapping("/link")
    public ResponseEntity<java.util.Map<String, String>> requestLink(@RequestParam Long childId, @RequestParam String inviteCode) {
        userService.requestLink(childId, inviteCode);
        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("message", "Link request submitted");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/link-requests")
    public ResponseEntity<List<com.eldercare.server.entity.FamilyLinkRequest>> getLinkRequests(@RequestParam Long parentId) {
        return ResponseEntity.ok(userService.getPendingRequests(parentId));
    }

    @PostMapping("/handle-link")
    public ResponseEntity<java.util.Map<String, String>> handleLinkRequest(@RequestParam Long requestId, @RequestParam boolean accept) {
        userService.handleLinkRequest(requestId, accept);
        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("message", "Request " + (accept ? "accepted" : "rejected"));
        return ResponseEntity.ok(response);
    }


    @Data
    public static class PhoneRequest {
        private String phone;
    }

    @Data
    public static class RegisterRequest {
        private String phone;
        private String role;
        private String inviteCode;
    }

    @Data
    public static class LoginRequest {
        private String phone;
        private String code;
    }
}
