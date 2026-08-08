package com.testforge.backend.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.backend.ai.AiClient;
import com.testforge.backend.ai.dto.AiPrompt;
import com.testforge.backend.ai.dto.AiResult;
import com.testforge.backend.ai.prompt.PromptBuilder;
import com.testforge.backend.analysis.dto.*;
import com.testforge.backend.analysis.entity.*;
import com.testforge.backend.analysis.repository.*;
import com.testforge.backend.analysis.scan.GeneratedTestSpec;
import com.testforge.backend.analysis.scan.ScanFinding;
import com.testforge.backend.analysis.scan.StaticAnalysisEngine;
import com.testforge.backend.analysis.scan.StaticScanResult;
import com.testforge.backend.analysis.scan.TestScaffoldGenerator;
import com.testforge.backend.auth.entity.User;
import com.testforge.backend.common.exception.BadRequestException;
import com.testforge.backend.config.AsyncJobLauncher;
import com.testforge.backend.common.exception.ResourceNotFoundException;
import com.testforge.backend.project.dto.ProjectStructureSummary;
import com.testforge.backend.project.entity.Project;
import com.testforge.backend.project.entity.ProjectStatus;
import com.testforge.backend.project.service.ProjectService;
import com.testforge.backend.swaggerspec.dto.SwaggerParseResult;
import com.testforge.backend.swaggerspec.service.SwaggerParsingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Orchestrates analysis of one project.
 *
 * <p>The pipeline is deliberately ordered so that facts come first and opinion second:
 * <ol>
 *   <li><b>Static scan</b> — {@link StaticAnalysisEngine} walks the real extracted files, matches
 *       security rules with file/line evidence, counts the test surface and computes the risk score.
 *       This stage cannot be skipped and does not depend on any external service.</li>
 *   <li><b>Deterministic test generation</b> — {@link TestScaffoldGenerator} emits tests naming real
 *       routes, classes and functions found in that scan.</li>
 *   <li><b>AI enrichment (optional)</b> — narrative explanation, additional scenarios and prioritized
 *       recommendations. Persisted with {@link ResultOrigin#AI_SUGGESTION} and rendered separately.
 *       If no provider is configured, or a call fails, the run still completes successfully and
 *       records why AI output is absent.</li>
 * </ol>
 *
 * <p>The whole pipeline runs on a background executor. {@link #startAnalysis} returns as soon as the
 * run row is committed, and the frontend polls {@link #getLatestResult}. Previously this work happened
 * inline on the request thread, including several fixed multi-second sleeps between AI calls, which
 * made a normal analysis exceed typical proxy and browser timeouts.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    /**
     * Spacing between AI calls, applied only when a real provider is in use. Gemini's free tier
     * rate-limits aggressively; with no provider configured there is nothing to pace, so this is never
     * paid. It runs on a background thread, so it delays only the analysis job, not an HTTP response.
     */
    private static final long AI_CALL_SPACING_MS = 4_000;

    private final ProjectService projectService;
    private final SwaggerParsingService swaggerParsingService;
    private final StaticAnalysisEngine staticAnalysisEngine;
    private final TestScaffoldGenerator testScaffoldGenerator;
    private final ApiTestExecutionService apiTestExecutionService;
    private final PromptBuilder promptBuilder;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final AsyncJobLauncher asyncJobLauncher;

    private final AnalysisRunRepository analysisRunRepository;
    private final GeneratedTestRepository generatedTestRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final AiRequestLogRepository aiRequestLogRepository;

    public AnalysisService(ProjectService projectService, SwaggerParsingService swaggerParsingService,
                           StaticAnalysisEngine staticAnalysisEngine, TestScaffoldGenerator testScaffoldGenerator,
                           ApiTestExecutionService apiTestExecutionService,
                           PromptBuilder promptBuilder, AiClient aiClient, ObjectMapper objectMapper,
                           AsyncJobLauncher asyncJobLauncher,
                           AnalysisRunRepository analysisRunRepository,
                           GeneratedTestRepository generatedTestRepository,
                           SecurityFindingRepository securityFindingRepository,
                           RiskAssessmentRepository riskAssessmentRepository,
                           AiRequestLogRepository aiRequestLogRepository) {
        this.projectService = projectService;
        this.swaggerParsingService = swaggerParsingService;
        this.staticAnalysisEngine = staticAnalysisEngine;
        this.testScaffoldGenerator = testScaffoldGenerator;
        this.apiTestExecutionService = apiTestExecutionService;
        this.promptBuilder = promptBuilder;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.asyncJobLauncher = asyncJobLauncher;
        this.analysisRunRepository = analysisRunRepository;
        this.generatedTestRepository = generatedTestRepository;
        this.securityFindingRepository = securityFindingRepository;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.aiRequestLogRepository = aiRequestLogRepository;
    }

    // ─── Kick-off ────────────────────────────────────────────────────────────────

    /**
     * Validates the project, creates a RUNNING analysis row and hands the work to a background
     * executor. Returns immediately with the run's initial state for the frontend to poll.
     *
     * <p>Not wrapped in an outer transaction on purpose: the run row must be durably committed before
     * the async job (on a different thread and connection) tries to load it.
     */
    public AnalysisRunResponse startAnalysis(User user, Long projectId) {
        Project project = projectService.getOwnedProject(user, projectId);

        if (project.getStatus() == ProjectStatus.EXTRACTING) {
            throw new BadRequestException("This project is still being extracted. Wait for extraction to finish "
                    + "before starting analysis.");
        }
        ProjectStructureSummary structure = projectService.readStructureSummary(project);
        if (structure == null) {
            throw new BadRequestException("No structure summary is available for this project, so there is nothing "
                    + "to analyze. Re-upload the project archive.");
        }
        analysisRunRepository.findFirstByProjectIdOrderByStartedAtDesc(projectId).ifPresent(existing -> {
            if (existing.getStatus() == AnalysisStatus.RUNNING && isRecent(existing.getStartedAt())) {
                throw new BadRequestException("An analysis is already running for this project. Wait for it to "
                        + "finish, or reload to see its progress.");
            }
        });

        AnalysisRun run = new AnalysisRun();
        run.setProject(project);
        run.setStatus(AnalysisStatus.RUNNING);
        run.setProgressPercent(0);
        run.setCurrentStage("Queued");
        run = analysisRunRepository.save(run);

        projectService.updateStatus(project, ProjectStatus.ANALYZING);

        // Handed off via AsyncJobLauncher rather than calling a local @Async method: a self-invocation
        // bypasses the async proxy, which would run the whole pipeline inline and make this endpoint block
        // for its full duration while still claiming the work was queued.
        final Long runId = run.getId();
        asyncJobLauncher.launchAnalysisJob("analysis-" + runId, () -> runPipeline(runId, projectId));

        return toRunResponse(run);
    }

    /** A RUNNING row older than this is treated as abandoned (e.g. the process restarted mid-run). */
    private boolean isRecent(Instant startedAt) {
        return startedAt != null && startedAt.isAfter(Instant.now().minusSeconds(900));
    }

    // ─── Background pipeline ─────────────────────────────────────────────────────

    /** Runs the full pipeline. Always invoked on a background thread via {@link AsyncJobLauncher}. */
    public void runPipeline(Long runId, Long projectId) {
        AnalysisRun run = analysisRunRepository.findById(runId).orElse(null);
        if (run == null) {
            log.warn("Analysis run {} vanished before the pipeline started", runId);
            return;
        }
        // Loaded by id rather than via run.getProject(): this method runs on a background thread with no
        // request-bound persistence context, so touching the lazy association on the detached run would
        // throw LazyInitializationException.
        Project project = projectService.getByIdForBackgroundJob(projectId);

        try {
            ProjectStructureSummary structure = projectService.readStructureSummary(project);
            SwaggerParseResult swagger = tryParseSwagger(project);

            // Re-analysis replaces the previous run's findings/tests/risk rather than accumulating them.
            // Without this, dashboard totals would climb with every re-run and the same finding would
            // appear several times, so the numbers would measure re-runs instead of the project.
            clearPreviousResults(projectId);

            // ── Stage 1: deterministic scan of the real files ────────────────────
            updateProgress(run, 10, "Scanning project files");
            StaticScanResult scan = runStaticScan(project, structure);

            updateProgress(run, 35, "Recording measured findings");
            persistFindings(project, run, scan.findings());

            // ── Stage 2: tests derived from real routes and declared units ───────
            updateProgress(run, 50, "Generating tests from discovered routes");
            boolean hasLiveTarget = hasLiveTarget(project);
            List<GeneratedTestSpec> specs = testScaffoldGenerator.generate(
                    structure, swagger, hasLiveTarget,
                    structure != null ? structure.primaryLanguage() : null);
            List<String> generatedTitles = persistTests(project, run, specs);

            updateProgress(run, 60, "Computing risk assessment");
            persistRisk(project, run, scan);

            run.setStaticSummary(scan.summary());
            run.setObservations(limit(scan.observations(), 25));
            analysisRunRepository.save(run);

            // ── Stage 3: optional AI enrichment ──────────────────────────────────
            if (aiClient.isConfigured()) {
                updateProgress(run, 70, "Running AI enrichment");
                runAiEnrichment(project, run, structure, swagger, scan, generatedTitles);
            } else {
                run.setAiStatus(aiClient.getNotConfiguredMessage());
                run.setAiProvider(null);
                analysisRunRepository.save(run);
                log.info("Project {} analyzed with static analysis only (no AI provider configured)", projectId);
            }

            // ── Stage 4: actually execute what can be executed ───────────────────
            updateProgress(run, 90, "Executing runnable API tests");
            apiTestExecutionService.executeAll(project);

            run.setStatus(AnalysisStatus.COMPLETED);
            run.setProgressPercent(100);
            run.setCurrentStage("Completed");
            run.setCompletedAt(Instant.now());
            analysisRunRepository.save(run);
            projectService.updateStatus(project, ProjectStatus.ANALYZED);
            log.info("Analysis run {} for project {} completed", runId, projectId);

        } catch (Exception ex) {
            log.error("Analysis run {} for project {} failed: {}", runId, projectId, ex.getMessage(), ex);
            run.setStatus(AnalysisStatus.FAILED);
            run.setErrorMessage(describeFailure(ex));
            run.setCurrentStage("Failed");
            run.setCompletedAt(Instant.now());
            analysisRunRepository.save(run);
            projectService.updateStatus(project, ProjectStatus.FAILED);
        }
    }

    private StaticScanResult runStaticScan(Project project, ProjectStructureSummary structure) {
        Path root = project.getStoragePath() != null ? Paths.get(project.getStoragePath()) : null;
        if (root != null && Files.isDirectory(root)) {
            return staticAnalysisEngine.scan(root, structure, project.getName());
        }
        // URL-based projects have no source tree on disk. Rather than fabricate code-level findings for
        // them, scan an empty tree so every count is honestly zero and the unavailable-check notes
        // explain that source scanning needs an uploaded archive.
        log.info("Project {} has no extracted source tree; source-level scanning is not applicable", project.getId());
        return staticAnalysisEngine.scan(createEmptyScanRoot(), structure, project.getName());
    }

    private Path createEmptyScanRoot() {
        try {
            return Files.createTempDirectory("qpilot-empty-scan");
        } catch (Exception e) {
            throw new IllegalStateException("Could not prepare an empty scan directory: " + e.getMessage(), e);
        }
    }

    private boolean hasLiveTarget(Project project) {
        return (project.getTargetApiUrl() != null && !project.getTargetApiUrl().isBlank())
                || (project.getTargetUrl() != null && !project.getTargetUrl().isBlank());
    }

    // ─── Persistence of measured results ────────────────────────────────────────

    /**
     * Clears the previous run's results for this project.
     *
     * <p>Not annotated {@code @Transactional} here: this is called from {@code runPipeline} on the same
     * bean, and a self-invocation bypasses the transactional proxy, so the annotation would be
     * decoration rather than behaviour. Each repository delete carries its own {@code @Transactional}
     * instead, which is where the guarantee actually needs to live.
     */
    private void clearPreviousResults(Long projectId) {
        securityFindingRepository.deleteByProjectId(projectId);
        generatedTestRepository.deleteByProjectId(projectId);
        riskAssessmentRepository.deleteByProjectId(projectId);
    }

    private void persistFindings(Project project, AnalysisRun run, List<ScanFinding> findings) {
        List<SecurityFinding> entities = new ArrayList<>();
        for (ScanFinding finding : findings) {
            SecurityFinding entity = new SecurityFinding();
            entity.setProject(project);
            entity.setAnalysisRun(run);
            entity.setCategory(finding.category());
            entity.setSeverity(finding.severity());
            entity.setDescription(finding.description());
            entity.setRecommendation(finding.recommendation());
            entity.setLocation(finding.filePath());
            entity.setLineNumber(finding.lineNumber());
            entity.setEvidence(finding.evidence());
            entity.setRuleId(finding.ruleId());
            entity.setOccurrenceCount(finding.occurrenceCount());
            entity.setOrigin(ResultOrigin.STATIC_ANALYSIS);
            entities.add(entity);
        }
        securityFindingRepository.saveAll(entities);
    }

    private List<String> persistTests(Project project, AnalysisRun run, List<GeneratedTestSpec> specs) {
        List<String> titles = new ArrayList<>();
        List<GeneratedTest> entities = new ArrayList<>();
        for (GeneratedTestSpec spec : specs) {
            GeneratedTest test = new GeneratedTest();
            test.setProject(project);
            test.setAnalysisRun(run);
            test.setType(spec.type());
            test.setTitle(spec.title());
            test.setTargetName(spec.targetName());
            test.setFramework(spec.framework());
            test.setDescription(spec.description());
            test.setCode(spec.code());
            test.setOrigin(ResultOrigin.STATIC_ANALYSIS);
            test.setRequestMethod(spec.requestMethod());
            test.setRequestPath(spec.requestPath());
            test.setRequestBody(spec.requestBody());
            test.setExpectedStatusCodes(spec.expectedStatusCodes());

            if (spec.executable()) {
                // Stays GENERATED until ApiTestExecutionService actually runs it.
                test.setExecutionStatus(TestExecutionStatus.GENERATED);
            } else {
                test.setExecutionStatus(TestExecutionStatus.NOT_EXECUTABLE);
                test.setExecutionDetail(spec.notExecutableReason());
            }
            entities.add(test);
            titles.add(spec.type() + " " + spec.title() + " -> " + spec.targetName());
        }
        generatedTestRepository.saveAll(entities);
        return titles;
    }

    private void persistRisk(Project project, AnalysisRun run, StaticScanResult scan) {
        RiskAssessment risk = new RiskAssessment();
        risk.setProject(project);
        risk.setAnalysisRun(run);
        risk.setScore(scan.riskScore());
        risk.setReasons(limit(scan.riskReasons(), 15));
        risk.setScoreBreakdown(limit(scan.scoreBreakdown(), 15));
        risk.setTestedSurfacePercent(scan.testedSurfacePercent());
        risk.setTestedSurfaceBasis(scan.testedSurfaceBasis());
        risk.setCoverageGaps(limit(scan.coverageGaps(), 25));
        risk.setUnavailableChecks(limit(scan.metrics().unavailableChecks(), 10));
        risk.setSourceFileCount(scan.metrics().sourceFileCount());
        risk.setTestFileCount(scan.metrics().testFileCount());
        risk.setTotalLinesOfCode(scan.metrics().nonBlankLinesOfCode());
        risk.setEndpointCount(scan.metrics().endpointCount());
        risk.setEndpointsReferencedByTests(scan.metrics().endpointsReferencedByTests());
        risk.setCriticalFindingCount(countSeverity(scan.findings(), Severity.CRITICAL));
        risk.setHighFindingCount(countSeverity(scan.findings(), Severity.HIGH));
        risk.setMediumFindingCount(countSeverity(scan.findings(), Severity.MEDIUM));
        risk.setLowFindingCount(countSeverity(scan.findings(), Severity.LOW));
        riskAssessmentRepository.save(risk);
    }

    private int countSeverity(List<ScanFinding> findings, Severity severity) {
        return (int) findings.stream().filter(f -> f.severity() == severity).count();
    }

    // ─── AI enrichment (advisory only, always attributed) ───────────────────────

    /**
     * Runs the optional AI agents. Each agent's failure is contained: a failing call is logged to the
     * audit table and noted in {@code aiStatus}, but never aborts the run and never causes measured
     * results to be discarded.
     */
    private void runAiEnrichment(Project project, AnalysisRun run, ProjectStructureSummary structure,
                                 SwaggerParseResult swagger, StaticScanResult scan, List<String> generatedTitles) {
        String projectContext = promptBuilder.buildProjectContext(structure, swagger);
        String measuredFacts = promptBuilder.buildMeasuredFacts(scan);
        List<String> aiNotes = new ArrayList<>();

        // Agent 1: narrative code understanding
        AiResult summaryResult = callAgent(project, run,
                promptBuilder.codeSummaryPrompt(project.getName(), projectContext));
        if (summaryResult.success()) {
            try {
                AiCodeSummaryPayload payload = objectMapper.readValue(summaryResult.rawJson(), AiCodeSummaryPayload.class);
                run.setAiSummary(payload.summary());
                run.setAiSummaryJson(summaryResult.rawJson());
            } catch (Exception e) {
                aiNotes.add("Code-understanding response could not be parsed: " + e.getMessage());
            }
        } else {
            aiNotes.add("Code understanding unavailable: " + summaryResult.errorMessage());
        }

        pauseBetweenAiCalls();

        // Agent 2: additional security suggestions the pattern scanner cannot reach
        AiResult securityResult = callAgent(project, run, promptBuilder.securityAnalysisPrompt(
                project.getName(), projectContext, promptBuilder.formatFindings(scan.findings())));
        if (securityResult.success()) {
            int added = persistAiFindings(project, run, securityResult.rawJson(), aiNotes);
            if (added > 0) {
                aiNotes.add(added + " additional security suggestion(s) were contributed by AI and are labelled "
                        + "as unverified suggestions requiring human review.");
            }
        } else {
            aiNotes.add("AI security review unavailable: " + securityResult.errorMessage());
        }

        pauseBetweenAiCalls();

        // Agent 3: extra test scenarios beyond the deterministic per-endpoint suite
        AiResult testResult = callAgent(project, run, promptBuilder.testGenerationPrompt(
                project.getName(), projectContext, promptBuilder.formatGeneratedTestTitles(generatedTitles)));
        if (testResult.success()) {
            int added = persistAiTests(project, run, testResult.rawJson(), aiNotes);
            if (added > 0) {
                aiNotes.add(added + " additional test scenario(s) were suggested by AI. They are marked "
                        + "NOT_EXECUTABLE because they describe multi-step flows QPilot does not drive automatically.");
            }
        } else {
            aiNotes.add("AI test ideation unavailable: " + testResult.errorMessage());
        }

        pauseBetweenAiCalls();

        // Agent 4: prioritized recommendations grounded in the measured facts
        AiResult recommendationsResult = callAgent(project, run,
                promptBuilder.recommendationsPrompt(project.getName(), projectContext, measuredFacts));
        if (recommendationsResult.success()) {
            try {
                AiRecommendationsPayload payload = objectMapper.readValue(
                        recommendationsResult.rawJson(), AiRecommendationsPayload.class);
                List<String> observations = new ArrayList<>(run.getObservations());
                if (payload.testStrategy() != null && !payload.testStrategy().isBlank()) {
                    observations.add("AI test strategy: " + payload.testStrategy());
                }
                if (payload.riskExplanation() != null && !payload.riskExplanation().isBlank()) {
                    observations.add("AI risk interpretation: " + payload.riskExplanation());
                }
                if (payload.priorityActions() != null) {
                    payload.priorityActions().stream().limit(6).forEach(action ->
                            observations.add("AI recommended action"
                                    + (action.effort() != null ? " (" + action.effort() + " effort)" : "")
                                    + ": " + action.title() + " — " + action.rationale()));
                }
                run.setObservations(limit(observations, 35));
            } catch (Exception e) {
                aiNotes.add("Recommendations response could not be parsed: " + e.getMessage());
            }
        } else {
            aiNotes.add("AI recommendations unavailable: " + recommendationsResult.errorMessage());
        }

        run.setAiProvider(aiClient.getActiveProviderName());
        run.setAiStatus(aiNotes.isEmpty()
                ? "AI enrichment completed. AI output is advisory and labelled separately from measured results."
                : truncate(String.join(" ", aiNotes), 990));
        analysisRunRepository.save(run);
    }

    private int persistAiFindings(Project project, AnalysisRun run, String rawJson, List<String> aiNotes) {
        try {
            AiSecurityAnalysisPayload payload = objectMapper.readValue(rawJson, AiSecurityAnalysisPayload.class);
            if (payload.findings() == null) {
                return 0;
            }
            List<SecurityFinding> entities = new ArrayList<>();
            for (AiSecurityAnalysisPayload.Item item : payload.findings().stream().limit(15).toList()) {
                SecurityFinding finding = new SecurityFinding();
                finding.setProject(project);
                finding.setAnalysisRun(run);
                finding.setCategory(item.category() != null ? item.category() : "AI_REVIEW");
                finding.setSeverity(parseEnum(Severity.class, item.severity(), Severity.MEDIUM));
                finding.setDescription(item.description());
                finding.setRecommendation(item.recommendation());
                finding.setLocation(item.location());
                // No evidence/lineNumber: an AI suggestion has not been verified against a file, and
                // populating those fields would make it indistinguishable from a scanned match.
                finding.setOrigin(ResultOrigin.AI_SUGGESTION);
                entities.add(finding);
            }
            securityFindingRepository.saveAll(entities);
            return entities.size();
        } catch (Exception e) {
            aiNotes.add("AI security findings could not be parsed: " + e.getMessage());
            return 0;
        }
    }

    private int persistAiTests(Project project, AnalysisRun run, String rawJson, List<String> aiNotes) {
        try {
            AiTestGenerationPayload payload = objectMapper.readValue(rawJson, AiTestGenerationPayload.class);
            if (payload.tests() == null) {
                return 0;
            }
            List<GeneratedTest> entities = new ArrayList<>();
            for (AiTestGenerationPayload.Item item : payload.tests().stream().limit(20).toList()) {
                if (item.code() == null || item.code().isBlank()) {
                    continue;
                }
                GeneratedTest test = new GeneratedTest();
                test.setProject(project);
                test.setAnalysisRun(run);
                test.setType(parseEnum(TestType.class, item.type(), TestType.INTEGRATION));
                test.setTitle(item.title() != null ? item.title() : "AI-suggested scenario");
                test.setTargetName(item.targetName());
                test.setFramework(item.framework());
                test.setDescription(item.description());
                test.setCode(item.code());
                test.setOrigin(ResultOrigin.AI_SUGGESTION);
                test.setExecutionStatus(TestExecutionStatus.NOT_EXECUTABLE);
                test.setExecutionDetail("AI-suggested scenario. QPilot does not execute it automatically: these "
                        + "describe multi-step flows with setup and state that only your own test harness can "
                        + "supply. Review the code, then run it in your pipeline.");
                entities.add(test);
            }
            generatedTestRepository.saveAll(entities);
            return entities.size();
        } catch (Exception e) {
            aiNotes.add("AI test scenarios could not be parsed: " + e.getMessage());
            return 0;
        }
    }

    private AiResult callAgent(Project project, AnalysisRun run, AiPrompt prompt) {
        AiResult result = aiClient.run(prompt);
        AiRequestLog logEntry = new AiRequestLog();
        logEntry.setProject(project);
        logEntry.setAnalysisRun(run);
        logEntry.setAgentType(prompt.agentType());
        logEntry.setProviderName(result.providerName());
        logEntry.setLatencyMs(result.latencyMs());
        logEntry.setSuccess(result.success());
        logEntry.setErrorMessage(truncate(result.errorMessage(), 1900));
        aiRequestLogRepository.save(logEntry);
        return result;
    }

    private void pauseBetweenAiCalls() {
        try {
            Thread.sleep(AI_CALL_SPACING_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ─── Read APIs ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AnalysisResultResponse getLatestResult(User user, Long projectId) {
        Project project = projectService.getOwnedProject(user, projectId);
        AnalysisRun run = analysisRunRepository.findFirstByProjectIdOrderByStartedAtDesc(project.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No analysis has been run for this project yet. Start one to populate results."));

        List<GeneratedTestResponse> tests = generatedTestRepository
                .findByProjectIdOrderByCreatedAtDesc(project.getId())
                .stream().map(this::toTestResponse).toList();
        List<SecurityFindingResponse> findings = securityFindingRepository
                .findByProjectIdOrderByCreatedAtDesc(project.getId())
                .stream().map(this::toFindingResponse).toList();
        RiskAssessmentResponse risk = riskAssessmentRepository
                .findFirstByProjectIdOrderByCreatedAtDesc(project.getId())
                .map(this::toRiskResponse).orElse(null);

        return new AnalysisResultResponse(toRunResponse(run), tests, findings, risk);
    }

    @Transactional(readOnly = true)
    public List<GeneratedTestResponse> listTests(User user, Long projectId) {
        Project project = projectService.getOwnedProject(user, projectId);
        return generatedTestRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
                .stream().map(this::toTestResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SecurityFindingResponse> listSecurityFindings(User user, Long projectId) {
        Project project = projectService.getOwnedProject(user, projectId);
        return securityFindingRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
                .stream().map(this::toFindingResponse).toList();
    }

    @Transactional(readOnly = true)
    public RiskAssessmentResponse getLatestRisk(User user, Long projectId) {
        Project project = projectService.getOwnedProject(user, projectId);
        return riskAssessmentRepository.findFirstByProjectIdOrderByCreatedAtDesc(project.getId())
                .map(this::toRiskResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No risk assessment exists for this project yet. Run an analysis first."));
    }

    /** Re-runs the executable tests on demand and reports the real outcome counts. */
    public TestExecutionSummary executeTests(User user, Long projectId) {
        Project project = projectService.getOwnedProject(user, projectId);
        long start = System.nanoTime();
        List<GeneratedTest> tests = apiTestExecutionService.executeAll(project);
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        int passed = 0;
        int failed = 0;
        int errored = 0;
        int skipped = 0;
        int notExecutable = 0;
        for (GeneratedTest test : tests) {
            switch (test.getExecutionStatus()) {
                case EXECUTED_PASSED -> passed++;
                case EXECUTED_FAILED -> failed++;
                case EXECUTION_ERROR -> errored++;
                case SKIPPED -> skipped++;
                case NOT_EXECUTABLE -> notExecutable++;
                case GENERATED -> { /* never attempted in this pass */ }
            }
        }

        String baseUrl = project.getTargetApiUrl() != null && !project.getTargetApiUrl().isBlank()
                ? project.getTargetApiUrl() : project.getTargetUrl();

        return new TestExecutionSummary(baseUrl, tests.size(), passed + failed + errored, passed, failed,
                errored, skipped, notExecutable, durationMs, Instant.now(),
                tests.stream().map(this::toTestResponse).toList());
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────────

    private void updateProgress(AnalysisRun run, int percent, String stage) {
        run.setProgressPercent(percent);
        run.setCurrentStage(stage);
        analysisRunRepository.save(run);
    }

    private SwaggerParseResult tryParseSwagger(Project project) {
        if (project.getSwaggerFilePath() == null) {
            return null;
        }
        try {
            return swaggerParsingService.parse(project.getSwaggerFilePath());
        } catch (Exception ex) {
            log.warn("Could not parse the OpenAPI spec attached to project {}: {}", project.getId(), ex.getMessage());
            return null;
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private AnalysisRunResponse toRunResponse(AnalysisRun run) {
        List<String> aiResponsibilities = List.of();
        List<String> aiObservations = List.of();
        if (run.getAiSummaryJson() != null) {
            try {
                AiCodeSummaryPayload payload = objectMapper.readValue(run.getAiSummaryJson(), AiCodeSummaryPayload.class);
                aiResponsibilities = payload.keyResponsibilities() != null ? payload.keyResponsibilities() : List.of();
                aiObservations = payload.notableObservations() != null ? payload.notableObservations() : List.of();
            } catch (Exception ignored) {
                // The narrative in aiSummary is still usable even if the structured lists are not.
            }
        }
        return new AnalysisRunResponse(
                run.getId(), run.getStatus(), run.getStaticSummary(), run.getObservations(),
                run.getAiSummary(), aiResponsibilities, aiObservations, run.getAiStatus(),
                run.getAiProvider(), run.getAiSummary() != null,
                run.getProgressPercent(), run.getCurrentStage(),
                run.getErrorMessage(), run.getStartedAt(), run.getCompletedAt());
    }

    private GeneratedTestResponse toTestResponse(GeneratedTest t) {
        return new GeneratedTestResponse(t.getId(), t.getType(), t.getTitle(), t.getTargetName(), t.getFramework(),
                t.getDescription(), t.getCode(), t.getOrigin(), t.getExecutionStatus(), t.getExecutionDetail(),
                t.getLastExecutedAt(), t.getExecutionLatencyMs(), t.getObservedHttpStatus(),
                t.getRequestMethod(), t.getRequestPath(), t.getExpectedStatusCodes(), t.getCreatedAt());
    }

    private SecurityFindingResponse toFindingResponse(SecurityFinding f) {
        return new SecurityFindingResponse(f.getId(), f.getCategory(), f.getSeverity(), f.getDescription(),
                f.getRecommendation(), f.getLocation(), f.getOrigin(), f.getLineNumber(), f.getEvidence(),
                f.getRuleId(), f.getOccurrenceCount(), f.getCreatedAt());
    }

    private RiskAssessmentResponse toRiskResponse(RiskAssessment r) {
        return new RiskAssessmentResponse(
                r.getScore(), r.getReasons(), r.getScoreBreakdown(),
                r.getTestedSurfacePercent(), r.getTestedSurfaceBasis(),
                r.getCoverageGaps(), r.getUnavailableChecks(),
                new RiskAssessmentResponse.MeasuredCounts(
                        r.getSourceFileCount(), r.getTestFileCount(), r.getTotalLinesOfCode(),
                        r.getEndpointCount(), r.getEndpointsReferencedByTests(),
                        r.getCriticalFindingCount(), r.getHighFindingCount(),
                        r.getMediumFindingCount(), r.getLowFindingCount()),
                r.getCreatedAt());
    }

    private <T> List<T> limit(List<T> values, int max) {
        if (values == null) {
            return List.of();
        }
        return values.size() > max ? new ArrayList<>(values.subList(0, max)) : new ArrayList<>(values);
    }

    /** Turns an exception into something a user can act on, rather than a bare class name. */
    private String describeFailure(Exception ex) {
        String detail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        return truncate("Analysis failed during processing: " + detail, 1900);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }
}
