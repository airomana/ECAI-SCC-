package com.eldercare.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank private String phone;
    @NotBlank private String code;       // 验证码
    @NotBlank private String role;       // parent / child
    private String inviteCode;           // 子女端可选
}
