package com.testforge.backend.dashboard;

import com.testforge.backend.analysis.entity.SecurityFinding;
import com.testforge.backend.analysis.repository.GeneratedTestRepository;
import com.testforge.backend.analysis.repository.RiskAssessmentRepository;
import com.testforge.backend.analysis.repository.SecurityFindingRepository;
import com.testforge.backend.auth.entity.User;
import com.testforge.backend.dashboard.dto.DashboardStatsResponse;
import com.testforge.backend.project.entity.ProjectStatus;
import com.testforge.backend.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregates real metrics across all of a user's projects for the dashboard.
 */
@Service
public class DashboardService {

    private final ProjectRepository projectRepository;
    private final GeneratedTestRepository generatedTestRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;

    public DashboardService(ProjectRepository projectRepository,
                            GeneratedTestRepository generatedTestRepository,
                            SecurityFindingRepository securityFindingRepository,
                            RiskAssessmentRepository riskAssessmentRepository) {
        this.projectRepository = projectRepository;
        this.generatedTestRepository = generatedTestRepository;
        this.securityFindingRepository = securityFindingRepository;
        this.riskAssessmentRepository = riskAssessmentRepository;
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats(User user) {
        Long userId = user.getId();

        int totalProjects = projectRepository.countByOwnerId(userId);
        int analyzedProjects = projectRepository.countByOwnerIdAndStatus(userId, ProjectStatus.ANALYZED);
        long totalTests = generatedTestRepository.countByOwner(userId);
        long totalFindings = securityFindingRepository.countByOwner(userId);

        Double avgCoverage = riskAssessmentRepository.avgCoverageByOwner(userId);
        Double avgRisk = riskAssessmentRepository.avgRiskScoreByOwner(userId);

        // Test type distribution
        List<Object[]> typeRaw = generatedTestRepository.countByTypeGrouped(userId);
        List<DashboardStatsResponse.TestTypeCount> testDistribution = typeRaw.stream()
                .map(row -> new DashboardStatsResponse.TestTypeCount(
                        row[0] != null ? row[0].toString() : "UNKNOWN",
                        ((Number) row[1]).longValue()))
                .collect(Collectors.toList());

        // Top 3 security findings as advice
        List<SecurityFinding> topFindings = securityFindingRepository.findTopByOwner(userId);
        List<DashboardStatsResponse.SecurityAdvice> topAdvice = topFindings.stream()
                .limit(5)
                .map(f -> new DashboardStatsResponse.SecurityAdvice(
                        f.getCategory(),
                        f.getSeverity().name(),
                        f.getDescription(),
                        f.getRecommendation()))
                .collect(Collectors.toList());

        return new DashboardStatsResponse(
                totalProjects,
                analyzedProjects,
                (int) totalTests,
                (int) totalFindings,
                avgCoverage != null ? Math.round(avgCoverage * 10.0) / 10.0 : 0.0,
                avgRisk != null ? Math.round(avgRisk * 10.0) / 10.0 : 0.0,
                testDistribution,
                topAdvice
        );
    }
}
