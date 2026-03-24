package com.eldercare.server.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String phone;

    private String role; // "parent" or "child"

    private String inviteCode;

    private String familyId;

    private String nickname;

    private Long createdAt;

    private Long lastLoginAt;
}
