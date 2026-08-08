package com.testforge.backend.analysis.entity;

import com.testforge.backend.project.entity.Project;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A computed risk/quality assessment for one analysis run.
 *
 * <p>Every number here is derived from counts measured off the project's real files by
 * {@code StaticAnalysisEngine} — never estimated by a language model and never synthesized. The raw
 * inputs are persisted alongside the score precisely so the score can be re-derived and audited:
 * if {@link #score} says 47, {@link #scoreBreakdown} says which measured facts contributed how many
 * points, and the count fields below say what was actually counted.
 *
 * <p>Note the deliberate absence of a "code coverage %" field. Real line coverage requires executing
 * the project's own test suite under an instrumentation agent, which QPilot does not do for uploaded
 * archives. {@link #testedSurfacePercent} is a different, honestly-named measurement whose exact
 * meaning is recorded in {@link #testedSurfaceBasis}.
 */
@Entity
@Table(name = "risk_assessments")
public class RiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false, unique = true)
    private AnalysisRun analysisRun;

    /** 0-100, computed by a fixed weighted formula over the measured counts below. */
    @Column(nullable = false)
    private int score;

    @ElementCollection
    @CollectionTable(name = "risk_assessment_reasons", joinColumns = @JoinColumn(name = "risk_assessment_id"))
    @Column(name = "reason", length = 1000)
    private List<String> reasons = new ArrayList<>();

    /**
     * Line-by-line arithmetic behind {@link #score}, e.g. "2 HIGH findings x 8 = +16". Stored so the
     * UI can show the user exactly how the number was reached rather than asking them to trust it.
     */
    @ElementCollection
    @CollectionTable(name = "risk_assessment_score_breakdown", joinColumns = @JoinColumn(name = "risk_assessment_id"))
    @Column(name = "line_item", length = 500)
    private List<String> scoreBreakdown = new ArrayList<>();

    /** Percentage of the measured test surface that has corresponding tests. See {@link #testedSurfaceBasis}. */
    private int testedSurfacePercent;

    /** Plain-English statement of what {@link #testedSurfacePercent} actually measured. */
    @Column(length = 500)
    private String testedSurfaceBasis;

    // ─── Raw measured inputs (all counted from real files on disk) ───────────────

    private int sourceFileCount;
    private int testFileCount;
    private long totalLinesOfCode;
    private int endpointCount;
    private int endpointsReferencedByTests;
    private int criticalFindingCount;
    private int highFindingCount;
    private int mediumFindingCount;
    private int lowFindingCount;

    @ElementCollection
    @CollectionTable(name = "risk_assessment_coverage_gaps", joinColumns = @JoinColumn(name = "risk_assessment_id"))
    @Column(name = "gap", length = 1000)
    private List<String> coverageGaps = new ArrayList<>();

    /**
     * Checks that could not be performed for this project, with the reason. Surfaced in the UI as
     * "Not available" rather than being silently omitted (which would read as "nothing to report").
     */
    @ElementCollection
    @CollectionTable(name = "risk_assessment_unavailable_checks", joinColumns = @JoinColumn(name = "risk_assessment_id"))
    @Column(name = "check_note", length = 500)
    private List<String> unavailableChecks = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RiskAssessment() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public AnalysisRun getAnalysisRun() { return analysisRun; }
    public void setAnalysisRun(AnalysisRun analysisRun) { this.analysisRun = analysisRun; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }

    public List<String> getScoreBreakdown() { return scoreBreakdown; }
    public void setScoreBreakdown(List<String> scoreBreakdown) { this.scoreBreakdown = scoreBreakdown; }

    public int getTestedSurfacePercent() { return testedSurfacePercent; }
    public void setTestedSurfacePercent(int testedSurfacePercent) { this.testedSurfacePercent = testedSurfacePercent; }

    public String getTestedSurfaceBasis() { return testedSurfaceBasis; }
    public void setTestedSurfaceBasis(String testedSurfaceBasis) { this.testedSurfaceBasis = testedSurfaceBasis; }

    public int getSourceFileCount() { return sourceFileCount; }
    public void setSourceFileCount(int sourceFileCount) { this.sourceFileCount = sourceFileCount; }

    public int getTestFileCount() { return testFileCount; }
    public void setTestFileCount(int testFileCount) { this.testFileCount = testFileCount; }

    public long getTotalLinesOfCode() { return totalLinesOfCode; }
    public void setTotalLinesOfCode(long totalLinesOfCode) { this.totalLinesOfCode = totalLinesOfCode; }

    public int getEndpointCount() { return endpointCount; }
    public void setEndpointCount(int endpointCount) { this.endpointCount = endpointCount; }

    public int getEndpointsReferencedByTests() { return endpointsReferencedByTests; }
    public void setEndpointsReferencedByTests(int endpointsReferencedByTests) { this.endpointsReferencedByTests = endpointsReferencedByTests; }

    public int getCriticalFindingCount() { return criticalFindingCount; }
    public void setCriticalFindingCount(int criticalFindingCount) { this.criticalFindingCount = criticalFindingCount; }

    public int getHighFindingCount() { return highFindingCount; }
    public void setHighFindingCount(int highFindingCount) { this.highFindingCount = highFindingCount; }

    public int getMediumFindingCount() { return mediumFindingCount; }
    public void setMediumFindingCount(int mediumFindingCount) { this.mediumFindingCount = mediumFindingCount; }

    public int getLowFindingCount() { return lowFindingCount; }
    public void setLowFindingCount(int lowFindingCount) { this.lowFindingCount = lowFindingCount; }

    public List<String> getCoverageGaps() { return coverageGaps; }
    public void setCoverageGaps(List<String> coverageGaps) { this.coverageGaps = coverageGaps; }

    public List<String> getUnavailableChecks() { return unavailableChecks; }
    public void setUnavailableChecks(List<String> unavailableChecks) { this.unavailableChecks = unavailableChecks; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
