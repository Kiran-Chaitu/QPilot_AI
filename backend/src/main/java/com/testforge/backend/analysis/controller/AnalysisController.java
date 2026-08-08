package com.testforge.backend.analysis.controller;

import com.testforge.backend.analysis.dto.AnalysisResultResponse;
import com.testforge.backend.analysis.dto.AnalysisRunResponse;
import com.testforge.backend.analysis.dto.GeneratedTestResponse;
import com.testforge.backend.analysis.dto.RiskAssessmentResponse;
import com.testforge.backend.analysis.dto.SecurityFindingResponse;
import com.testforge.backend.analysis.dto.TestExecutionSummary;
import com.testforge.backend.analysis.service.AnalysisService;
import com.testforge.backend.auth.entity.User;
import com.testforge.backend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{id}")
@Tag(name = "Analysis")
@SecurityRequirement(name = "bearerAuth")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * Starts an analysis and returns 202 with the run's initial state.
     *
     * <p>Deliberately asynchronous: a scan plus optional AI enrichment can take a minute or more, and
     * holding an HTTP request open for that long runs into proxy and browser timeouts. Clients poll
     * {@code GET /analysis} and read {@code run.progressPercent} / {@code run.currentStage}.
     */
    @PostMapping("/analyze")
    @Operation(summary = "Start analysis (asynchronous — poll GET /analysis for progress)")
    public ResponseEntity<ApiResponse<AnalysisRunResponse>> analyze(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        AnalysisRunResponse run = analysisService.startAnalysis(user, id);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("Analysis started. Poll this project's analysis endpoint for progress.", run));
    }

    @GetMapping("/analysis")
    @Operation(summary = "Latest analysis result, including live progress while a run is in flight")
    public ResponseEntity<ApiResponse<AnalysisResultResponse>> latestAnalysis(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(analysisService.getLatestResult(user, id)));
    }

    @GetMapping("/tests")
    public ResponseEntity<ApiResponse<List<GeneratedTestResponse>>> listTests(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(analysisService.listTests(user, id)));
    }

    /**
     * Executes the project's executable tests against its own configured target and returns the real
     * outcome counts. The target is taken from the project record, never from the request.
     */
    @PostMapping("/tests/execute")
    @Operation(summary = "Really execute the runnable API tests against the project's configured target")
    public ResponseEntity<ApiResponse<TestExecutionSummary>> executeTests(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        TestExecutionSummary summary = analysisService.executeTests(user, id);
        String message = summary.executed() == 0
                ? "No tests could be executed. See each test's execution detail for the reason."
                : summary.executed() + " test(s) executed: " + summary.passed() + " passed, "
                        + summary.failed() + " failed, " + summary.errored() + " errored.";
        return ResponseEntity.ok(ApiResponse.ok(message, summary));
    }

    @GetMapping("/security-report")
    public ResponseEntity<ApiResponse<List<SecurityFindingResponse>>> securityReport(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(analysisService.listSecurityFindings(user, id)));
    }

    @GetMapping("/risk")
    public ResponseEntity<ApiResponse<RiskAssessmentResponse>> risk(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(analysisService.getLatestRisk(user, id)));
    }
}
