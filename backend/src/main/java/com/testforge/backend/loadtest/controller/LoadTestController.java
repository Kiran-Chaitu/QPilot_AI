package com.testforge.backend.loadtest.controller;

import com.testforge.backend.common.dto.ApiResponse;
import com.testforge.backend.loadtest.dto.LoadTestRequest;
import com.testforge.backend.loadtest.dto.LoadTestResponse;
import com.testforge.backend.loadtest.service.LoadTestService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loadtest/run")
@Tag(name = "Load Tester")
@SecurityRequirement(name = "bearerAuth")
public class LoadTestController {

    private final LoadTestService loadTestService;

    public LoadTestController(LoadTestService loadTestService) {
        this.loadTestService = loadTestService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LoadTestResponse>> run(@RequestBody LoadTestRequest req) {
        LoadTestResponse result = loadTestService.runLoadTest(req);
        return ResponseEntity.ok(ApiResponse.ok("Load test simulation completed successfully", result));
    }
}
