package com.testforge.backend.loadtest.controller;

import com.testforge.backend.auth.entity.User;
import com.testforge.backend.common.dto.ApiResponse;
import com.testforge.backend.loadtest.dto.LoadTestRequest;
import com.testforge.backend.loadtest.dto.LoadTestResponse;
import com.testforge.backend.loadtest.service.LoadTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Load-test API. Starting a run is asynchronous: the POST returns 202 with a run id, and the client
 * polls the run resource for live metrics. Runs can be stopped mid-flight, and every run is retained
 * so history and the dashboard's performance trend are built from real past executions.
 */
@RestController
@RequestMapping("/api/loadtest")
@Tag(name = "Load Testing")
@SecurityRequirement(name = "bearerAuth")
public class LoadTestController {

    private final LoadTestService loadTestService;

    public LoadTestController(LoadTestService loadTestService) {
        this.loadTestService = loadTestService;
    }

    @PostMapping("/runs")
    @Operation(summary = "Start a real load test (asynchronous — poll GET /runs/{id} for live metrics)")
    public ResponseEntity<ApiResponse<LoadTestResponse>> start(
            @AuthenticationPrincipal User user, @Valid @RequestBody LoadTestRequest request) {
        LoadTestResponse run = loadTestService.startRun(user, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("Load test started. Poll this run for live metrics.", run));
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "Current state of a run, with live partial metrics while it is still running")
    public ResponseEntity<ApiResponse<LoadTestResponse>> get(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(loadTestService.getRun(user, id)));
    }

    @PostMapping("/runs/{id}/stop")
    @Operation(summary = "Stop a running load test; metrics measured so far are retained")
    public ResponseEntity<ApiResponse<LoadTestResponse>> stop(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        LoadTestResponse run = loadTestService.stopRun(user, id);
        return ResponseEntity.ok(ApiResponse.ok(
                "Stop requested. The run will finalize with the metrics measured so far.", run));
    }

    @GetMapping("/runs")
    @Operation(summary = "Recent load-test runs for the current user")
    public ResponseEntity<ApiResponse<List<LoadTestResponse>>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(loadTestService.listRuns(user)));
    }
}
