package com.testforge.backend.analysis.entity;

import com.testforge.backend.project.entity.Project;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "generated_tests")
public class GeneratedTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private AnalysisRun analysisRun;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestType type;

    @Column(nullable = false)
    private String title;

    private String targetName;

    private String framework;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String code;

    /** Whether this test was derived from scanned project facts or suggested by an LLM. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultOrigin origin = ResultOrigin.STATIC_ANALYSIS;

    /**
     * Real execution lifecycle. Starts at {@link TestExecutionStatus#GENERATED} and is only advanced
     * by {@code ApiTestExecutionService} after an actual HTTP round-trip.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestExecutionStatus executionStatus = TestExecutionStatus.GENERATED;

    /** Human-readable outcome or the reason a test could not be run. */
    @Column(length = 1000)
    private String executionDetail;

    private Instant lastExecutedAt;

    /** Measured wall-clock duration of the last real execution, in milliseconds. Null if never run. */
    private Long executionLatencyMs;

    /** Observed HTTP status of the last real execution. Null if never run or not an HTTP test. */
    private Integer observedHttpStatus;

    /**
     * For API tests: the concrete method/path this test exercises, extracted from the project's own
     * discovered routes. These are what make an API test genuinely executable against a live target.
     */
    private String requestMethod;

    @Column(length = 1000)
    private String requestPath;

    /** Request body to send when executing this test (malformed-payload / injection probes carry one). */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String requestBody;

    /** Status codes considered a pass for this test, e.g. "200,201" or "401,403". */
    private String expectedStatusCodes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public GeneratedTest() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public AnalysisRun getAnalysisRun() { return analysisRun; }
    public void setAnalysisRun(AnalysisRun analysisRun) { this.analysisRun = analysisRun; }

    public TestType getType() { return type; }
    public void setType(TestType type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public ResultOrigin getOrigin() { return origin; }
    public void setOrigin(ResultOrigin origin) { this.origin = origin; }

    public TestExecutionStatus getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(TestExecutionStatus executionStatus) { this.executionStatus = executionStatus; }

    public String getExecutionDetail() { return executionDetail; }
    public void setExecutionDetail(String executionDetail) { this.executionDetail = executionDetail; }

    public Instant getLastExecutedAt() { return lastExecutedAt; }
    public void setLastExecutedAt(Instant lastExecutedAt) { this.lastExecutedAt = lastExecutedAt; }

    public Long getExecutionLatencyMs() { return executionLatencyMs; }
    public void setExecutionLatencyMs(Long executionLatencyMs) { this.executionLatencyMs = executionLatencyMs; }

    public Integer getObservedHttpStatus() { return observedHttpStatus; }
    public void setObservedHttpStatus(Integer observedHttpStatus) { this.observedHttpStatus = observedHttpStatus; }

    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }

    public String getRequestPath() { return requestPath; }
    public void setRequestPath(String requestPath) { this.requestPath = requestPath; }

    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }

    public String getExpectedStatusCodes() { return expectedStatusCodes; }
    public void setExpectedStatusCodes(String expectedStatusCodes) { this.expectedStatusCodes = expectedStatusCodes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
