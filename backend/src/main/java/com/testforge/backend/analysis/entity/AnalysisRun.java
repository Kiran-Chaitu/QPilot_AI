package com.testforge.backend.analysis.entity;

import com.testforge.backend.project.entity.Project;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "analysis_runs")
public class AnalysisRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status = AnalysisStatus.RUNNING;

    /** Human-readable narrative summary produced by the Code Understanding agent. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String codeSummary;

    /** Full raw JSON returned by the Code Understanding agent (keyResponsibilities, notableObservations, ...). */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String codeSummaryJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private Instant startedAt = Instant.now();

    private Instant completedAt;

    public AnalysisRun() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public AnalysisStatus getStatus() { return status; }
    public void setStatus(AnalysisStatus status) { this.status = status; }

    public String getCodeSummary() { return codeSummary; }
    public void setCodeSummary(String codeSummary) { this.codeSummary = codeSummary; }

    public String getCodeSummaryJson() { return codeSummaryJson; }
    public void setCodeSummaryJson(String codeSummaryJson) { this.codeSummaryJson = codeSummaryJson; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
