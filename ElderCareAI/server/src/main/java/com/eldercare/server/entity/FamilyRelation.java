package com.eldercare.server.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "family_relation")
public class FamilyRelation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String familyId;

    private Long parentUserId;

    private Long childUserId;

    private Long linkedAt;
}
