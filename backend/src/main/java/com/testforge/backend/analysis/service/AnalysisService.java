package com.testforge.backend.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.backend.ai.AiClient;
import com.testforge.backend.ai.dto.AgentType;
import com.testforge.backend.ai.dto.AiPrompt;
import com.testforge.backend.ai.dto.AiResult;
import com.testforge.backend.ai.prompt.PromptBuilder;
import com.testforge.backend.analysis.dto.*;
import com.testforge.backend.analysis.entity.*;
import com.testforge.backend.analysis.repository.*;
import com.testforge.backend.auth.entity.User;
import com.testforge.backend.common.exception.BadRequestException;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Orchestrates the multi-agent AI pipeline for a single project:
 * Code Understanding -> Test Generation -> Security Analysis -> Coverage & Risk.
 * Spring Boot's job here is purely to sequence calls, persist results, and keep
 * the pipeline resilient (a failure in one agent doesn't necessarily abort the
 * rest) — all actual reasoning happens inside {@link AiClient}.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final ProjectService projectService;
    private final SwaggerParsingService swaggerParsingService;
    private final PromptBuilder promptBuilder;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    private final AnalysisRunRepository analysisRunRepository;
    private final GeneratedTestRepository generatedTestRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final AiRequestLogRepository aiRequestLogRepository;

    public AnalysisService(ProjectService projectService, SwaggerParsingService swaggerParsingService,
                            PromptBuilder promptBuilder, AiClient aiClient, ObjectMapper objectMapper,
                            AnalysisRunRepository analysisRunRepository, GeneratedTestRepository generatedTestRepository,
                            SecurityFindingRepository securityFindingRepository,
                            RiskAssessmentRepository riskAssessmentRepository,
                            AiRequestLogRepository aiRequestLogRepository) {
        this.projectService = projectService;
        this.swaggerParsingService = swaggerParsingService;
        this.promptBuilder = promptBuilder;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.analysisRunRepository = analysisRunRepository;
        this.generatedTestRepository = generatedTestRepository;
        this.securityFindingRepository = securityFindingRepository;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.aiRequestLogRepository = aiRequestLogRepository;
    }

    // Intentionally NOT @Transactional: each step below persists independently so a
    // failure partway through the pipeline still keeps whatever was already generated,
    // and the FAILED status update is guaranteed to be committed rather than rolled back.
    public AnalysisResultResponse analyze(User user, Long projectId) {
        Project project = projectService.getOwnedProject(user, projectId);
        ProjectStructureSummary structure = projectService.readStructureSummary(project);
        if (structure == null) {
            throw new BadRequestException("Project has not been analyzed for structure yet; re-upload the project");
        }

        SwaggerParseResult swagger = tryParseSwagger(project);
        String projectContext = promptBuilder.buildProjectContext(structure, swagger);

        projectService.updateStatus(project, ProjectStatus.ANALYZING);

        AnalysisRun run = new AnalysisRun();
        run.setProject(project);
        run.setStatus(AnalysisStatus.RUNNING);
        run = analysisRunRepository.save(run);

        try {
            // 1. Code Understanding agent
            AiPrompt codeSummaryPrompt = promptBuilder.codeSummaryPrompt(project.getName(), projectContext);
            AiResult codeSummaryResult = callAgent(project, run, codeSummaryPrompt);
            if (!codeSummaryResult.success()) {
                throw new IllegalStateException("Code Understanding agent failed: " + codeSummaryResult.errorMessage());
            }
            AiCodeSummaryPayload codeSummary = objectMapper.readValue(codeSummaryResult.rawJson(), AiCodeSummaryPayload.class);
            run.setCodeSummary(codeSummary.summary());
            run.setCodeSummaryJson(codeSummaryResult.rawJson());
            analysisRunRepository.save(run);

            // 2. Test Generation agent
            List<GeneratedTest> savedTests = new ArrayList<>();
            AiPrompt testPrompt = promptBuilder.testGenerationPrompt(project.getName(), projectContext, codeSummaryResult.rawJson());
            AiResult testResult = callAgent(project, run, testPrompt);
            if (testResult.success()) {
                try {
                    AiTestGenerationPayload payload = objectMapper.readValue(testResult.rawJson(), AiTestGenerationPayload.class);
                    for (AiTestGenerationPayload.Item item : payload.tests()) {
                        GeneratedTest test = new GeneratedTest();
                        test.setProject(project);
                        test.setAnalysisRun(run);
                        test.setType(parseEnum(TestType.class, item.type(), TestType.UNIT));
                        test.setTitle(item.title());
                        test.setTargetName(item.targetName());
                        test.setFramework(item.framework());
                        test.setDescription(item.description());
                        test.setCode(item.code());
                        savedTests.add(generatedTestRepository.save(test));
                    }
                } catch (Exception parseEx) {
                    log.warn("Could not parse test generation payload for project {}: {}", projectId, parseEx.getMessage());
                }
            } else {
                log.warn("Test Generation agent failed for project {}: {}", projectId, testResult.errorMessage());
            }

            // 3. Security Analysis agent
            List<SecurityFinding> savedFindings = new ArrayList<>();
            AiPrompt securityPrompt = promptBuilder.securityAnalysisPrompt(project.getName(), projectContext, codeSummaryResult.rawJson());
            AiResult securityResult = callAgent(project, run, securityPrompt);
            if (securityResult.success()) {
                try {
                    AiSecurityAnalysisPayload payload = objectMapper.readValue(securityResult.rawJson(), AiSecurityAnalysisPayload.class);
                    for (AiSecurityAnalysisPayload.Item item : payload.findings()) {
                        SecurityFinding finding = new SecurityFinding();
                        finding.setProject(project);
                        finding.setAnalysisRun(run);
                        finding.setCategory(item.category());
                        finding.setSeverity(parseEnum(Severity.class, item.severity(), Severity.MEDIUM));
                        finding.setDescription(item.description());
                        finding.setRecommendation(item.recommendation());
                        finding.setLocation(item.location());
                        savedFindings.add(securityFindingRepository.save(finding));
                    }
                } catch (Exception parseEx) {
                    log.warn("Could not parse security analysis payload for project {}: {}", projectId, parseEx.getMessage());
                }
            } else {
                log.warn("Security Analysis agent failed for project {}: {}", projectId, securityResult.errorMessage());
            }

            // 4. Coverage & Risk agent
            RiskAssessment riskAssessment = null;
            AiPrompt riskPrompt = promptBuilder.riskScorePrompt(project.getName(), projectContext, codeSummaryResult.rawJson(),
                    savedTests.size(), savedFindings.size());
            AiResult riskResult = callAgent(project, run, riskPrompt);
            if (riskResult.success()) {
                try {
                    AiRiskScorePayload payload = objectMapper.readValue(riskResult.rawJson(), AiRiskScorePayload.class);
                    riskAssessment = new RiskAssessment();
                    riskAssessment.setProject(project);
                    riskAssessment.setAnalysisRun(run);
                    riskAssessment.setScore(payload.score());
                    riskAssessment.setReasons(payload.reasons());
                    riskAssessment.setCoverageEstimatePercent(payload.coverageEstimatePercent());
                    riskAssessment.setCoverageGaps(payload.coverageGaps());
                    riskAssessment = riskAssessmentRepository.save(riskAssessment);
                } catch (Exception parseEx) {
                    log.warn("Could not parse risk score payload for project {}: {}", projectId, parseEx.getMessage());
                }
            } else {
                log.warn("Risk agent failed for project {}: {}", projectId, riskResult.errorMessage());
            }

            run.setStatus(AnalysisStatus.COMPLETED);
            run.setCompletedAt(java.time.Instant.now());
            analysisRunRepository.save(run);
            projectService.updateStatus(project, ProjectStatus.ANALYZED);

            return new AnalysisResultResponse(
                    toRunResponse(run, codeSummary),
                    savedTests.stream().map(this::toTestResponse).toList(),
                    savedFindings.stream().map(this::toFindingResponse).toList(),
                    riskAssessment != null ? toRiskResponse(riskAssessment) : null
            );
        } catch (Exception ex) {
            log.error("Analysis pipeline failed for project {}: {}", projectId, ex.getMessage(), ex);
            run.setStatus(AnalysisStatus.FAILED);
            run.setErrorMessage(ex.getMessage());
            run.setCompletedAt(java.time.Instant.now());
            analysisRunRepository.save(run);
            projectService.updateStatus(project, ProjectStatus.FAILED);
            throw new com.testforge.backend.common.exception.ApiException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Analysis failed: " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public AnalysisResultResponse getLatestResult(User user, Long projectId) {
        Project project = projectService.getOwnedProject(user, projectId);
        AnalysisRun run = analysisRunRepository.findFirstByProjectIdOrderByStartedAtDesc(project.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No analysis has been run yet for this project"));

        AiCodeSummaryPayload codeSummary = null;
        if (run.getCodeSummaryJson() != null) {
            try {
                codeSummary = objectMapper.readValue(run.getCodeSummaryJson(), AiCodeSummaryPayload.class);
            } catch (Exception ignored) {
            }
        }

        List<GeneratedTestResponse> tests = generatedTestRepository.findByAnalysisRunIdOrderByCreatedAtDesc(run.getId())
                .stream().map(this::toTestResponse).toList();
        List<SecurityFindingResponse> findings = securityFindingRepository.findByAnalysisRunIdOrderByCreatedAtDesc(run.getId())
                .stream().map(this::toFindingResponse).toList();
        RiskAssessmentResponse risk = riskAssessmentRepository.findByAnalysisRunId(run.getId())
                .map(this::toRiskResponse).orElse(null);

        return new AnalysisResultResponse(toRunResponse(run, codeSummary), tests, findings, risk);
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
                .orElseThrow(() -> new ResourceNotFoundException("No risk assessment available yet for this project"));
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
        logEntry.setErrorMessage(result.errorMessage());
        aiRequestLogRepository.save(logEntry);
        return result;
    }

    private SwaggerParseResult tryParseSwagger(Project project) {
        if (project.getSwaggerFilePath() == null) {
            return null;
        }
        try {
            return swaggerParsingService.parse(project.getSwaggerFilePath());
        } catch (Exception ex) {
            log.warn("Could not parse uploaded Swagger spec for project {}: {}", project.getId(), ex.getMessage());
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

    private AnalysisRunResponse toRunResponse(AnalysisRun run, AiCodeSummaryPayload codeSummary) {
        return new AnalysisRunResponse(
                run.getId(), run.getStatus(), run.getCodeSummary(),
                codeSummary != null ? codeSummary.keyResponsibilities() : List.of(),
                codeSummary != null ? codeSummary.notableObservations() : List.of(),
                run.getErrorMessage(), run.getStartedAt(), run.getCompletedAt(), aiClient.getActiveProviderName()
        );
    }

    private GeneratedTestResponse toTestResponse(GeneratedTest t) {
        return new GeneratedTestResponse(t.getId(), t.getType(), t.getTitle(), t.getTargetName(), t.getFramework(),
                t.getDescription(), t.getCode(), t.getCreatedAt());
    }

    private SecurityFindingResponse toFindingResponse(SecurityFinding f) {
        return new SecurityFindingResponse(f.getId(), f.getCategory(), f.getSeverity(), f.getDescription(),
                f.getRecommendation(), f.getLocation(), f.getCreatedAt());
    }

    private RiskAssessmentResponse toRiskResponse(RiskAssessment r) {
        return new RiskAssessmentResponse(r.getScore(), r.getReasons(), r.getCoverageEstimatePercent(),
                r.getCoverageGaps(), r.getCreatedAt());
    }
}
