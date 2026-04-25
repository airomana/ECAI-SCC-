package com.eldercare.backend.controller;

import com.eldercare.backend.common.ApiResponse;
import com.eldercare.backend.dto.LoginRequest;
import com.eldercare.backend.dto.RegisterRequest;
import com.eldercare.backend.service.AuthService;
import com.eldercare.backend.service.SmsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SmsService smsService;

    /** 发送验证码 */
    @PostMapping("/send-code")
    public ApiResponse<?> sendCode(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            return ApiResponse.error("手机号格式不正确");
        }
        String debugCode = smsService.sendCode(phone);
        // 测试模式下把验证码返回给 App，生产模式 debugCode 为 null
        return ApiResponse.success(debugCode != null ? Map.of("debugCode", debugCode) : null);
    }

    /** 注册 */
    @PostMapping("/register")
    public ApiResponse<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            return ApiResponse.success(authService.register(req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /** 登录 */
    @PostMapping("/login")
    public ApiResponse<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            return ApiResponse.success(authService.login(req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
