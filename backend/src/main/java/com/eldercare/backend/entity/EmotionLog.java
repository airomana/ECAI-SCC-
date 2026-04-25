package com.eldercare.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "emotion_log", indexes = {
    @Index(columnList = "userId"),
    @Index(columnList = "dayTimestamp")
})
public class EmotionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long dayTimestamp;

    @Column(nullable = false, length = 20)
    private String dominantEmotion;

    @Column(columnDefinition = "TEXT")
    private String emotionDistributionJson = "{}";

    private Integer conversationCount = 0;
    private Integer totalMessages = 0;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private Boolean sentToFamily = false;

    @Column(nullable = false)
    private Long createdAt;
}
