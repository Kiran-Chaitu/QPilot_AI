package com.testforge.backend.analysis.controller;

import com.testforge.backend.analysis.dto.AnalysisResultResponse;
import com.testforge.backend.analysis.dto.GeneratedTestResponse;
import com.testforge.backend.analysis.dto.RiskAssessmentResponse;
import com.testforge.backend.analysis.dto.SecurityFindingResponse;
import com.testforge.backend.analysis.service.AnalysisService;
import com.testforge.backend.auth.entity.User;
import com.testforge.backend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{id}")
@Tag(name = "AI Analysis")
@SecurityRequirement(name = "bearerAuth")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<AnalysisResultResponse>> analyze(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Analysis complete", analysisService.analyze(user, id)));
    }

    @GetMapping("/analysis")
    public ResponseEntity<ApiResponse<AnalysisResultResponse>> latestAnalysis(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(analysisService.getLatestResult(user, id)));
    }

    @GetMapping("/tests")
    public ResponseEntity<ApiResponse<List<GeneratedTestResponse>>> listTests(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(analysisService.listTests(user, id)));
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
