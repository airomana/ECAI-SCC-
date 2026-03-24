package com.eldercare.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.List;

@Data
@Entity
@Table(name = "health_profile")
public class HealthProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String name;

    private Integer age;
    
    private Boolean shareHealth;
    private Boolean shareDiet;
    private Boolean shareContacts;

    @ElementCollection
    private List<String> diseases;

    @ElementCollection
    private List<String> allergies;

    private Long updatedAt;
}
