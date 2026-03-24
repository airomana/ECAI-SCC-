package com.eldercare.server.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "weekly_reports")
@Data
public class WeeklyReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id", nullable = false)
    private Long parentId;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String reportContent;

    @Column(nullable = false)
    private Long generatedAt;
}
