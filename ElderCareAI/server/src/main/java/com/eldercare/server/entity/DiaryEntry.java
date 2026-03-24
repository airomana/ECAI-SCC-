package com.eldercare.server.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "diary_entry")
public class DiaryEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long date;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String emotion;

    @Column(columnDefinition = "TEXT")
    private String aiResponse;
}
