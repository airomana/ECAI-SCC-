package com.eldercare.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank private String phone;
    @NotBlank private String code;
}
