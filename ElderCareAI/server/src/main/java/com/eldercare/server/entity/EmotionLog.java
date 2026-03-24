package com.eldercare.server.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "emotion_logs")
@Data
public class EmotionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String emotion;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false)
    private Long timestamp;
}
