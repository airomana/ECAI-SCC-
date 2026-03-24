package com.eldercare.server.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "family_link_request")
public class FamilyLinkRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long parentUserId;

    private Long childUserId;
    
    @Transient
    private String childPhone;

    private String status; // "pending", "accepted", "rejected"

    private Long createdAt;

    private Long handledAt;
}