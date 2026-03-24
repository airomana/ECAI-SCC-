package com.eldercare.server.service;

import com.eldercare.server.entity.FamilyLinkRequest;
import com.eldercare.server.entity.FamilyRelation;
import com.eldercare.server.entity.User;
import com.eldercare.server.repository.FamilyLinkRequestRepository;
import com.eldercare.server.repository.FamilyRelationRepository;
import com.eldercare.server.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * 用户业务逻辑服务
 * 处理最核心的注册和家庭绑定逻辑
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final FamilyRelationRepository familyRelationRepository;
    private final FamilyLinkRequestRepository familyLinkRequestRepository;

    public UserService(UserRepository userRepository, FamilyRelationRepository familyRelationRepository, FamilyLinkRequestRepository familyLinkRequestRepository) {
        this.userRepository = userRepository;
        this.familyRelationRepository = familyRelationRepository;
        this.familyLinkRequestRepository = familyLinkRequestRepository;
    }

    public Optional<User> findByPhone(String phone) {
        return userRepository.findByPhone(phone);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    // 生成6位随机数字邀请码，供父母分享给子女
    private String generateInviteCode() {
        return String.valueOf(new Random().nextInt(900000) + 100000);
    }

    // 生成全局唯一的家庭ID，用于将父母和子女关联在一起
    private String generateFamilyId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 处理用户注册的核心逻辑
     * 1. 父母注册：自动生成邀请码和家庭ID
     * 2. 子女注册：校验邀请码，如果正确则自动绑定到父母的家庭ID
     */
    @Transactional
    public User register(String phone, String role, String inviteCode) {
        if (userRepository.findByPhone(phone).isPresent()) {
            throw new RuntimeException("Phone number already registered");
        }

        User user = new User();
        user.setPhone(phone);
        user.setRole(role);
        long now = System.currentTimeMillis();
        user.setCreatedAt(now);
        user.setLastLoginAt(now);

        if ("parent".equals(role)) {
            user.setInviteCode(generateInviteCode());
            user.setFamilyId(generateFamilyId());
            // Parent registration ignores any provided inviteCode
            return userRepository.save(user);
        } else if ("child".equals(role)) {
            User savedChild = userRepository.save(user);
            if (inviteCode != null && !inviteCode.isEmpty()) {
                Optional<User> parentOpt = userRepository.findByInviteCode(inviteCode);
                if (parentOpt.isPresent() && "parent".equals(parentOpt.get().getRole())) {
                    User parent = parentOpt.get();
                    // Create pending request instead of auto-linking
                    FamilyLinkRequest request = new FamilyLinkRequest();
                    request.setParentUserId(parent.getId());
                    request.setChildUserId(savedChild.getId());
                    request.setStatus("pending");
                    request.setCreatedAt(now);
                    familyLinkRequestRepository.save(request);
                } else {
                    throw new RuntimeException("Invalid invite code");
                }
            }
            return savedChild;
        } else {
            throw new RuntimeException("Invalid role");
        }
    }

    public List<FamilyLinkRequest> getPendingRequests(Long parentId) {
        List<FamilyLinkRequest> requests = familyLinkRequestRepository.findByParentUserIdAndStatusOrderByCreatedAtDesc(parentId, "pending");
        for (FamilyLinkRequest req : requests) {
            userRepository.findById(req.getChildUserId()).ifPresent(child -> {
                req.setChildPhone(child.getPhone());
            });
        }
        return requests;
    }

    @Transactional
    public void requestLink(Long childId, String inviteCode) {
        User child = userRepository.findById(childId).orElseThrow(() -> new RuntimeException("Child not found"));
        User parent = userRepository.findByInviteCode(inviteCode).orElseThrow(() -> new RuntimeException("Invalid invite code"));
        
        Optional<FamilyLinkRequest> existingRequest = familyLinkRequestRepository.findByParentUserIdAndChildUserIdAndStatus(parent.getId(), childId, "pending");
        if (existingRequest.isPresent()) {
            // Already requested
            return;
        }

        FamilyLinkRequest request = new FamilyLinkRequest();
        request.setParentUserId(parent.getId());
        request.setChildUserId(childId);
        request.setStatus("pending");
        request.setCreatedAt(System.currentTimeMillis());
        familyLinkRequestRepository.save(request);
    }

    @Transactional
    public void handleLinkRequest(Long requestId, boolean accept) {
        FamilyLinkRequest request = familyLinkRequestRepository.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Request not found"));
            
        if (!"pending".equals(request.getStatus())) {
            throw new RuntimeException("Request already handled");
        }

        request.setStatus(accept ? "accepted" : "rejected");
        request.setHandledAt(System.currentTimeMillis());
        familyLinkRequestRepository.save(request);

        if (accept) {
            User parent = userRepository.findById(request.getParentUserId()).orElseThrow();
            User child = userRepository.findById(request.getChildUserId()).orElseThrow();
            
            child.setFamilyId(parent.getFamilyId());
            userRepository.save(child);

            FamilyRelation relation = new FamilyRelation();
            relation.setFamilyId(parent.getFamilyId());
            relation.setParentUserId(parent.getId());
            relation.setChildUserId(child.getId());
            relation.setLinkedAt(System.currentTimeMillis());
            familyRelationRepository.save(relation);
        }
    }
}
