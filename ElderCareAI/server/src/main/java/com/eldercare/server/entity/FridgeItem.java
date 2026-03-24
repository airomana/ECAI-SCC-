package com.eldercare.server.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "fridge_item")
public class FridgeItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long userId;

    private String name;

    private String category;

    private Long addedAt;

    private Long expiryAt;
}
