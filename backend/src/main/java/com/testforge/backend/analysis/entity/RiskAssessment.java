package com.testforge.backend.analysis.entity;

import com.testforge.backend.project.entity.Project;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    @Column(nullable = false)
    private int score;

    @ElementCollection
    @CollectionTable(name = "risk_assessment_reasons", joinColumns = @JoinColumn(name = "risk_assessment_id"))
    @Column(name = "reason", length = 1000)
    private List<String> reasons = new ArrayList<>();

    private int coverageEstimatePercent;

    @ElementCollection
    @CollectionTable(name = "risk_assessment_coverage_gaps", joinColumns = @JoinColumn(name = "risk_assessment_id"))
    @Column(name = "gap", length = 1000)
    private List<String> coverageGaps = new ArrayList<>();

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

    public int getCoverageEstimatePercent() { return coverageEstimatePercent; }
    public void setCoverageEstimatePercent(int coverageEstimatePercent) { this.coverageEstimatePercent = coverageEstimatePercent; }

    public List<String> getCoverageGaps() { return coverageGaps; }
    public void setCoverageGaps(List<String> coverageGaps) { this.coverageGaps = coverageGaps; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
