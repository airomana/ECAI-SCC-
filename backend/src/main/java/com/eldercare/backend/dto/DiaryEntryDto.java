package com.eldercare.backend.dto;

import lombok.Data;

@Data
public class DiaryEntryDto {
    private Long date;
    private String content;
    private String emotion;
    private String aiResponse;
}
