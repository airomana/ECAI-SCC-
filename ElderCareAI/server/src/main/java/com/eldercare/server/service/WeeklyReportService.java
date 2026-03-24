package com.eldercare.server.service;

import com.eldercare.server.entity.WeeklyReport;
import com.eldercare.server.repository.WeeklyReportRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WeeklyReportService {
    private final WeeklyReportRepository repository;

    public WeeklyReportService(WeeklyReportRepository repository) {
        this.repository = repository;
    }

    public WeeklyReport save(WeeklyReport report) {
        return repository.save(report);
    }

    public List<WeeklyReport> getReportsForChild(Long childId) {
        return repository.findByChildIdOrderByGeneratedAtDesc(childId);
    }
}
