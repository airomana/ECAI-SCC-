package com.eldercare.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private Long userId;
    private String role;
    private String inviteCode;
    private String familyId;
    private Boolean linkPending;
}
