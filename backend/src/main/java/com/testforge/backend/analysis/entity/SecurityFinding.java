package com.testforge.backend.analysis.entity;

import com.testforge.backend.project.entity.Project;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "security_findings")
public class SecurityFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private AnalysisRun analysisRun;

    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String recommendation;

    private String location;

    /** Provenance — scanned evidence vs. AI suggestion. Never inferred; always set explicitly. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultOrigin origin = ResultOrigin.STATIC_ANALYSIS;

    /** 1-based line number within {@link #location}, when the finding came from a real file match. */
    private Integer lineNumber;

    /**
     * The actual source line that triggered the rule, truncated. This is what makes a
     * {@link ResultOrigin#STATIC_ANALYSIS} finding verifiable: the user can open the cited file at the
     * cited line and see the same text. AI suggestions leave this null.
     */
    @Column(length = 500)
    private String evidence;

    /** Rule identifier that produced this finding, so results are traceable back to a specific check. */
    private String ruleId;

    /**
     * How many times this rule matched across the project. The row itself reports one representative
     * occurrence; this count keeps the total honest when matches are capped for display.
     */
    private Integer occurrenceCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public SecurityFinding() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public AnalysisRun getAnalysisRun() { return analysisRun; }
    public void setAnalysisRun(AnalysisRun analysisRun) { this.analysisRun = analysisRun; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public ResultOrigin getOrigin() { return origin; }
    public void setOrigin(ResultOrigin origin) { this.origin = origin; }

    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public Integer getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(Integer occurrenceCount) { this.occurrenceCount = occurrenceCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
