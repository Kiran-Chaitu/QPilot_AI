package com.testforge.backend.report.repository;

import com.testforge.backend.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {
    Optional<Report> findFirstByProjectIdOrderByGeneratedAtDesc(Long projectId);
}
