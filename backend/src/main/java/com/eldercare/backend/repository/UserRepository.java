package com.eldercare.backend.repository;

import com.eldercare.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhone(String phone);
    Optional<User> findByInviteCode(String inviteCode);
    List<User> findByFamilyId(String familyId);
    boolean existsByPhone(String phone);
}
