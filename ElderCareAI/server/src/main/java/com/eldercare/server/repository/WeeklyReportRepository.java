package com.eldercare.server.repository;

import com.eldercare.server.entity.WeeklyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {
    List<WeeklyReport> findByChildIdOrderByGeneratedAtDesc(Long childId);
    List<WeeklyReport> findByParentIdOrderByGeneratedAtDesc(Long parentId);
}
