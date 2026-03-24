package com.eldercare.server.repository;

import com.eldercare.server.entity.FamilyRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FamilyRelationRepository extends JpaRepository<FamilyRelation, Long> {
    List<FamilyRelation> findByFamilyId(String familyId);
    List<FamilyRelation> findByParentUserId(Long parentUserId);
    List<FamilyRelation> findByChildUserId(Long childUserId);
}
