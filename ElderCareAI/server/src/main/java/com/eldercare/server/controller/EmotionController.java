package com.eldercare.server.controller;

import com.eldercare.server.entity.EmotionLog;
import com.eldercare.server.service.EmotionLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/emotion")
public class EmotionController {
    private final EmotionLogService service;

    public EmotionController(EmotionLogService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<EmotionLog>> getEmotions(@RequestParam Long userId) {
        return ResponseEntity.ok(service.findByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<EmotionLog> addEmotion(@RequestBody EmotionLog log) {
        return ResponseEntity.ok(service.save(log));
    }
}
