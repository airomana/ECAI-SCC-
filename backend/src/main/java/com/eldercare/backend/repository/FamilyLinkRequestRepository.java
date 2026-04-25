package com.eldercare.backend.repository;

import com.eldercare.backend.entity.FamilyLinkRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FamilyLinkRequestRepository extends JpaRepository<FamilyLinkRequest, Long> {
    List<FamilyLinkRequest> findByParentUserIdAndStatus(Long parentUserId, String status);
    List<FamilyLinkRequest> findByChildUserIdAndStatus(Long childUserId, String status);
    Optional<FamilyLinkRequest> findByParentUserIdAndChildUserIdAndStatus(Long parentUserId, Long childUserId, String status);
}
