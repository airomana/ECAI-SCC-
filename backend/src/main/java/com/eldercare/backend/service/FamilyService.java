package com.eldercare.backend.service;

import com.eldercare.backend.entity.FamilyLinkRequest;
import com.eldercare.backend.entity.User;
import com.eldercare.backend.repository.FamilyLinkRequestRepository;
import com.eldercare.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FamilyService {

    private final UserRepository userRepo;
    private final FamilyLinkRequestRepository linkRepo;

    /** 子女端通过邀请码发起绑定申请 */
    @Transactional
    public void requestLink(Long childUserId, String inviteCode) {
        User child = userRepo.findById(childUserId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!"child".equals(child.getRole())) {
            throw new IllegalArgumentException("只有子女端可以发起绑定");
        }
        if (child.getFamilyId() != null) {
            throw new IllegalArgumentException("您已绑定家庭");
        }

        User parent = userRepo.findByInviteCode(inviteCode.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("邀请码无效"));
        if (!"parent".equals(parent.getRole())) {
            throw new IllegalArgumentException("邀请码无效");
        }

        // 检查是否已有 pending 申请
        linkRepo.findByParentUserIdAndChildUserIdAndStatus(parent.getId(), childUserId, "pending")
                .ifPresent(r -> { throw new IllegalArgumentException("已提交过绑定申请，等待父母确认"); });

        FamilyLinkRequest req = new FamilyLinkRequest();
        req.setParentUserId(parent.getId());
        req.setChildUserId(childUserId);
        req.setStatus("pending");
        req.setCreatedAt(System.currentTimeMillis());
        linkRepo.save(req);
    }

    /** 父母端查看待处理的绑定申请 */
    public List<FamilyLinkRequest> getPendingRequests(Long parentUserId) {
        return linkRepo.findByParentUserIdAndStatus(parentUserId, "pending");
    }

    /** 父母端审批绑定申请 */
    @Transactional
    public void handleRequest(Long parentUserId, Long requestId, boolean approve) {
        FamilyLinkRequest req = linkRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("申请不存在"));
        if (!req.getParentUserId().equals(parentUserId)) {
            throw new IllegalArgumentException("无权操作");
        }
        if (!"pending".equals(req.getStatus())) {
            throw new IllegalArgumentException("申请已处理");
        }

        req.setStatus(approve ? "approved" : "rejected");
        req.setHandledAt(System.currentTimeMillis());
        linkRepo.save(req);

        if (approve) {
            // 将子女的 familyId 设为父母的 familyId
            User parent = userRepo.findById(parentUserId).orElseThrow();
            User child = userRepo.findById(req.getChildUserId()).orElseThrow();
            child.setFamilyId(parent.getFamilyId());
            userRepo.save(child);
        }
    }

    /** 获取家庭成员列表 */
    public List<User> getFamilyMembers(Long userId) {
        User user = userRepo.findById(userId).orElseThrow();
        if (user.getFamilyId() == null) return List.of();
        return userRepo.findByFamilyId(user.getFamilyId());
    }

    /** 子女端查询自己的绑定申请状态 */
    public List<FamilyLinkRequest> getMyLinkRequests(Long childUserId) {
        return linkRepo.findByChildUserIdAndStatus(childUserId, "pending");
    }
}
