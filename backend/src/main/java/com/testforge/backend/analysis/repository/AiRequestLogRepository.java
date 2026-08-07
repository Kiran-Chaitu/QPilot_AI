package com.testforge.backend.analysis.repository;

import com.testforge.backend.analysis.entity.AiRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, Long> {
}
