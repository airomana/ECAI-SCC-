package com.eldercare.server.controller;

import com.eldercare.server.entity.WeeklyReport;
import com.eldercare.server.service.WeeklyReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/report")
public class ReportController {
    private final WeeklyReportService service;

    public ReportController(WeeklyReportService service) {
        this.service = service;
    }

    @GetMapping("/weekly")
    public ResponseEntity<List<WeeklyReport>> getWeeklyReports(@RequestParam Long childId) {
        return ResponseEntity.ok(service.getReportsForChild(childId));
    }
}
