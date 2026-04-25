package com.eldercare.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "family_link_request", indexes = {
    @Index(columnList = "parentUserId"),
    @Index(columnList = "childUserId"),
    @Index(columnList = "status")
})
public class FamilyLinkRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long parentUserId;

    @Column(nullable = false)
    private Long childUserId;

    /** pending / approved / rejected */
    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(nullable = false)
    private Long createdAt;

    private Long handledAt;
}
