package com.testforge.backend.dashboard;

import com.testforge.backend.analysis.entity.RiskAssessment;
import com.testforge.backend.analysis.entity.SecurityFinding;
import com.testforge.backend.analysis.entity.Severity;
import com.testforge.backend.analysis.entity.TestExecutionStatus;
import com.testforge.backend.analysis.repository.GeneratedTestRepository;
import com.testforge.backend.analysis.repository.RiskAssessmentRepository;
import com.testforge.backend.analysis.repository.SecurityFindingRepository;
import com.testforge.backend.auth.entity.User;
import com.testforge.backend.dashboard.dto.DashboardStatsResponse;
import com.testforge.backend.loadtest.repository.LoadTestRunRepository;
import com.testforge.backend.project.entity.ProjectStatus;
import com.testforge.backend.project.repository.ProjectRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates the dashboard's numbers from stored rows.
 *
 * <p>Every figure here is a database aggregate over records that were written by a real operation.
 * Nothing is defaulted to a flattering value when data is missing: a workspace with no analyses reports
 * zeros and nulls, which the UI renders as empty states rather than as a healthy-looking dashboard.
 *
 * <p>The test counts are split along the line that matters. Generated is how many tests exist; executed,
 * passed and failed come only from {@code TestExecutionStatus} values that
 * {@code ApiTestExecutionService} wrote after an actual HTTP round-trip. That is why a project with a
 * large generated suite and no reachable target shows a large "generated" number and zero "passed".
 */
@Service
public class DashboardService {

    private static final int MAX_ADVICE_ITEMS = 5;
    private static final int MAX_RISK_HISTORY_POINTS = 30;

    private final ProjectRepository projectRepository;
    private final GeneratedTestRepository generatedTestRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final LoadTestRunRepository loadTestRunRepository;

    public DashboardService(ProjectRepository projectRepository,
                            GeneratedTestRepository generatedTestRepository,
                            SecurityFindingRepository securityFindingRepository,
                            RiskAssessmentRepository riskAssessmentRepository,
                            LoadTestRunRepository loadTestRunRepository) {
        this.projectRepository = projectRepository;
        this.generatedTestRepository = generatedTestRepository;
        this.securityFindingRepository = securityFindingRepository;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.loadTestRunRepository = loadTestRunRepository;
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats(User user) {
        Long userId = user.getId();

        int totalProjects = projectRepository.countByOwnerId(userId);
        int analyzedProjects = projectRepository.countByOwnerIdAndStatus(userId, ProjectStatus.ANALYZED);
        long totalTests = generatedTestRepository.countByOwner(userId);
        long totalFindings = securityFindingRepository.countByOwner(userId);

        Map<TestExecutionStatus, Long> executionCounts = readExecutionCounts(userId);
        int passed = intValue(executionCounts.get(TestExecutionStatus.EXECUTED_PASSED));
        int failed = intValue(executionCounts.get(TestExecutionStatus.EXECUTED_FAILED));
        int errored = intValue(executionCounts.get(TestExecutionStatus.EXECUTION_ERROR));
        int notExecutable = intValue(executionCounts.get(TestExecutionStatus.NOT_EXECUTABLE));

        Map<Severity, Long> severityCounts = readSeverityCounts(userId);

        // Null rather than 0.0 when no assessment exists: zero risk and "not yet measured" are different
        // claims, and only the UI can decide how to present the second one.
        Double avgTestedSurface = riskAssessmentRepository.avgTestedSurfaceByOwner(userId);
        Double avgRisk = riskAssessmentRepository.avgRiskScoreByOwner(userId);

        List<DashboardStatsResponse.TestTypeCount> testDistribution =
                generatedTestRepository.countByTypeGrouped(userId).stream()
                        .map(row -> new DashboardStatsResponse.TestTypeCount(
                                row[0] != null ? row[0].toString() : "UNKNOWN",
                                ((Number) row[1]).longValue()))
                        .toList();

        List<SecurityFinding> topFindings = securityFindingRepository.findTopByOwner(
                userId, PageRequest.of(0, MAX_ADVICE_ITEMS));
        List<DashboardStatsResponse.SecurityAdvice> topAdvice = topFindings.stream()
                .map(finding -> new DashboardStatsResponse.SecurityAdvice(
                        finding.getCategory(),
                        finding.getSeverity().name(),
                        finding.getDescription(),
                        finding.getRecommendation(),
                        finding.getOrigin().name(),
                        buildLocationLabel(finding)))
                .toList();

        return new DashboardStatsResponse(
                totalProjects, analyzedProjects,
                (int) totalTests, passed + failed + errored, passed, failed, errored, notExecutable,
                (int) totalFindings,
                intValue(severityCounts.get(Severity.CRITICAL)),
                intValue(severityCounts.get(Severity.HIGH)),
                intValue(severityCounts.get(Severity.MEDIUM)),
                intValue(severityCounts.get(Severity.LOW)),
                round1(avgTestedSurface), round1(avgRisk),
                testDistribution, topAdvice,
                buildRiskHistory(userId),
                buildLoadTestSummary(userId));
    }

    // ─── Aggregate readers ───────────────────────────────────────────────────────

    private Map<TestExecutionStatus, Long> readExecutionCounts(Long userId) {
        Map<TestExecutionStatus, Long> counts = new EnumMap<>(TestExecutionStatus.class);
        for (Object[] row : generatedTestRepository.countByExecutionStatusGrouped(userId)) {
            if (row[0] instanceof TestExecutionStatus status) {
                counts.put(status, ((Number) row[1]).longValue());
            }
        }
        return counts;
    }

    private Map<Severity, Long> readSeverityCounts(Long userId) {
        Map<Severity, Long> counts = new EnumMap<>(Severity.class);
        for (Object[] row : securityFindingRepository.countBySeverityGrouped(userId)) {
            if (row[0] instanceof Severity severity) {
                counts.put(severity, ((Number) row[1]).longValue());
            }
        }
        return counts;
    }

    /**
     * Builds the risk trend from stored assessments, oldest first. These are real historical points — one
     * per analysis run that produced an assessment — so the chart shows how the workspace actually moved
     * rather than a shape generated to look like a trend.
     */
    private List<DashboardStatsResponse.RiskPoint> buildRiskHistory(Long userId) {
        List<RiskAssessment> assessments = riskAssessmentRepository.findAllByOwnerChronologically(userId);
        List<DashboardStatsResponse.RiskPoint> points = new ArrayList<>();
        int skip = Math.max(0, assessments.size() - MAX_RISK_HISTORY_POINTS);
        for (RiskAssessment assessment : assessments.subList(skip, assessments.size())) {
            points.add(new DashboardStatsResponse.RiskPoint(
                    assessment.getProject().getName(),
                    assessment.getScore(),
                    assessment.getTestedSurfacePercent(),
                    assessment.getCreatedAt()));
        }
        return points;
    }

    /** Null when the user has never completed a load test, so the UI shows an empty state, not zeros. */
    private DashboardStatsResponse.LoadTestSummary buildLoadTestSummary(Long userId) {
        List<Object[]> rows = loadTestRunRepository.aggregateFinishedRuns(userId);
        if (rows.isEmpty() || rows.get(0) == null) {
            return null;
        }
        Object[] row = rows.get(0);
        long runCount = ((Number) row[0]).longValue();
        if (runCount == 0) {
            return null;
        }
        return new DashboardStatsResponse.LoadTestSummary(
                (int) runCount,
                ((Number) row[1]).longValue(),
                round1(((Number) row[2]).doubleValue()),
                round1(((Number) row[3]).doubleValue()),
                row[4] instanceof Instant instant ? instant : null);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /** Labels a finding's location, including the line number when the finding cites real file evidence. */
    private String buildLocationLabel(SecurityFinding finding) {
        if (finding.getLocation() == null) {
            return null;
        }
        return finding.getLineNumber() != null
                ? finding.getLocation() + ":" + finding.getLineNumber()
                : finding.getLocation();
    }

    private int intValue(Long value) {
        return value != null ? value.intValue() : 0;
    }

    private Double round1(Double value) {
        return value != null ? Math.round(value * 10.0) / 10.0 : null;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
