package com.eldercare.server.repository;

import com.eldercare.server.entity.FamilyLinkRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FamilyLinkRequestRepository extends JpaRepository<FamilyLinkRequest, Long> {
    List<FamilyLinkRequest> findByParentUserIdAndStatusOrderByCreatedAtDesc(Long parentUserId, String status);
    List<FamilyLinkRequest> findByChildUserIdAndStatusOrderByCreatedAtDesc(Long childUserId, String status);
    Optional<FamilyLinkRequest> findByParentUserIdAndChildUserIdAndStatus(Long parentUserId, Long childUserId, String status);
}