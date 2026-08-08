package com.testforge.backend.ratelimit.controller;

import com.testforge.backend.common.dto.ApiResponse;
import com.testforge.backend.ratelimit.dto.RateLimitTestRequest;
import com.testforge.backend.ratelimit.dto.RateLimitTestResponse;
import com.testforge.backend.ratelimit.service.RateLimitTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ratelimit")
@Tag(name = "Rate Limit Testing")
@SecurityRequirement(name = "bearerAuth")
public class RateLimitTestController {

    private final RateLimitTestService rateLimitTestService;

    public RateLimitTestController(RateLimitTestService rateLimitTestService) {
        this.rateLimitTestService = rateLimitTestService;
    }

    /**
     * Runs the probe synchronously — bounded by construction (a few hundred requests over a few seconds),
     * so unlike a load test it completes well within a normal request timeout.
     */
    @PostMapping("/probe")
    @Operation(summary = "Probe a target for real rate-limiting behaviour (burst + sustained phases)")
    public ResponseEntity<ApiResponse<RateLimitTestResponse>> probe(@Valid @RequestBody RateLimitTestRequest request) {
        RateLimitTestResponse result = rateLimitTestService.probe(request);
        String message = result.rateLimitingDetected()
                ? "Rate limiting was detected and confirmed by observation."
                : "No rate-limiting evidence was observed at the load applied.";
        return ResponseEntity.ok(ApiResponse.ok(message, result));
    }
}
