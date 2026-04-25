package com.eldercare.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "users", indexes = {
    @Index(columnList = "phone", unique = true),
    @Index(columnList = "inviteCode", unique = true),
    @Index(columnList = "familyId")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    /** parent 或 child */
    @Column(nullable = false, length = 10)
    private String role;

    /** 父母端生成的邀请码 */
    @Column(length = 32)
    private String inviteCode;

    /** 绑定后的家庭ID */
    @Column(length = 64)
    private String familyId;

    @Column(length = 50)
    private String nickname;

    /** 健康档案 JSON（HealthProfile + PersonalSituation 序列化） */
    @Column(columnDefinition = "TEXT")
    private String profileJson;

    @Column(nullable = false)
    private Long createdAt;

    @Column(nullable = false)
    private Long lastLoginAt;
}
