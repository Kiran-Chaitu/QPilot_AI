package com.testforge.backend.analysis.entity;

import com.testforge.backend.project.entity.Project;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One execution of the analysis pipeline for a project.
 *
 * <p>The pipeline has two halves and this entity keeps them separate all the way to the database. The
 * static half ({@link #staticSummary}, {@link #observations}) is produced by scanning real files and
 * always succeeds if the project extracted. The AI half ({@link #aiSummary}) is optional enrichment;
 * when no provider is configured or the provider fails, {@link #aiStatus} records why and the run
 * still completes rather than being reported as failed — the measured results are unaffected.
 *
 * <p>{@link #progressPercent}/{@link #currentStage} exist because the pipeline runs asynchronously on a
 * background executor. The HTTP request that starts a run returns immediately and the frontend polls
 * this row, so an analysis that takes a minute can never time out a request thread.
 */
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

    /** Factual narrative assembled from measured counts by the static engine. Always populated. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String staticSummary;

    /** Structural facts worth surfacing (file ratios, per-category finding counts, ...). */
    @ElementCollection
    @CollectionTable(name = "analysis_run_observations", joinColumns = @JoinColumn(name = "analysis_run_id"))
    @Column(name = "observation", length = 1000)
    private List<String> observations = new ArrayList<>();

    /** Narrative produced by the configured LLM, or null when AI enrichment did not run. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    /** Raw JSON from the AI code-understanding agent, retained for auditing what the model returned. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String aiSummaryJson;

    /**
     * Why AI enrichment is present or absent — e.g. "No AI provider configured", "Gemini call failed:
     * 429". Shown verbatim in the UI so an absent AI section is explained rather than mysterious.
     */
    @Column(length = 1000)
    private String aiStatus;

    /** Provider that produced the AI half, e.g. "gemini:gemini-2.0-flash". Null when AI did not run. */
    private String aiProvider;

    @Column(nullable = false)
    private int progressPercent = 0;

    @Column(length = 200)
    private String currentStage;

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

    public String getStaticSummary() { return staticSummary; }
    public void setStaticSummary(String staticSummary) { this.staticSummary = staticSummary; }

    public List<String> getObservations() { return observations; }
    public void setObservations(List<String> observations) { this.observations = observations; }

    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }

    public String getAiSummaryJson() { return aiSummaryJson; }
    public void setAiSummaryJson(String aiSummaryJson) { this.aiSummaryJson = aiSummaryJson; }

    public String getAiStatus() { return aiStatus; }
    public void setAiStatus(String aiStatus) { this.aiStatus = aiStatus; }

    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }

    public int getProgressPercent() { return progressPercent; }
    public void setProgressPercent(int progressPercent) { this.progressPercent = progressPercent; }

    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
