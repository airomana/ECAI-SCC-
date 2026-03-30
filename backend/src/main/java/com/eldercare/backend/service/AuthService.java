package com.eldercare.backend.service;

import com.eldercare.backend.dto.AuthResponse;
import com.eldercare.backend.dto.LoginRequest;
import com.eldercare.backend.dto.RegisterRequest;
import com.eldercare.backend.entity.FamilyLinkRequest;
import com.eldercare.backend.entity.User;
import com.eldercare.backend.repository.FamilyLinkRequestRepository;
import com.eldercare.backend.repository.UserRepository;
import com.eldercare.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final FamilyLinkRequestRepository linkRepo;
    private final SmsService smsService;
    private final JwtUtil jwtUtil;

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (!req.getPhone().matches("^1[3-9]\\d{9}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        if (!smsService.verifyCode(req.getPhone(), req.getCode())) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
        if (userRepo.existsByPhone(req.getPhone())) {
            throw new IllegalArgumentException("该手机号已注册，请直接登录");
        }
        if (!"parent".equals(req.getRole()) && !"child".equals(req.getRole())) {
            throw new IllegalArgumentException("角色参数错误");
        }

        long now = System.currentTimeMillis();
        User user = new User();
        user.setPhone(req.getPhone());
        user.setRole(req.getRole());
        user.setCreatedAt(now);
        user.setLastLoginAt(now);

        String generatedInviteCode = null;
        if ("parent".equals(req.getRole())) {
            generatedInviteCode = generateInviteCode();
            user.setInviteCode(generatedInviteCode);
            user.setFamilyId(UUID.randomUUID().toString());
        }

        user = userRepo.save(user);

        // 子女端：如果提供了邀请码，创建绑定申请
        boolean linkPending = false;
        if ("child".equals(req.getRole()) && req.getInviteCode() != null && !req.getInviteCode().isBlank()) {
            User parent = userRepo.findByInviteCode(req.getInviteCode().trim().toUpperCase()).orElse(null);
            if (parent != null && "parent".equals(parent.getRole())) {
                FamilyLinkRequest link = new FamilyLinkRequest();
                link.setParentUserId(parent.getId());
                link.setChildUserId(user.getId());
                link.setStatus("pending");
                link.setCreatedAt(now);
                linkRepo.save(link);
                linkPending = true;
            }
        }

        String token = jwtUtil.generateToken(user.getId(), user.getRole());
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .role(user.getRole())
                .inviteCode(generatedInviteCode)
                .linkPending(linkPending)
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        if (!req.getPhone().matches("^1[3-9]\\d{9}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        if (!smsService.verifyCode(req.getPhone(), req.getCode())) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }

        User user = userRepo.findByPhone(req.getPhone())
                .orElseThrow(() -> new IllegalArgumentException("该手机号未注册，请先注册"));

        user.setLastLoginAt(System.currentTimeMillis());
        userRepo.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getRole());
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .role(user.getRole())
                .inviteCode(user.getInviteCode())
                .familyId(user.getFamilyId())
                .build();
    }

    private String generateInviteCode() {
        SecureRandom random = new SecureRandom();
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(10);
            for (int i = 0; i < 10; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String code = sb.toString();
            if (userRepo.findByInviteCode(code).isEmpty()) return code;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
