package com.testforge.backend.e2etest.controller;

import com.testforge.backend.common.dto.ApiResponse;
import com.testforge.backend.e2etest.dto.E2eTestRequest;
import com.testforge.backend.e2etest.dto.E2eTestResponse;
import com.testforge.backend.e2etest.service.E2eTestService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/e2e-test")
@Tag(name = "E2E Browser Testing")
@SecurityRequirement(name = "bearerAuth")
public class E2eTestController {

    private final E2eTestService e2eTestService;

    public E2eTestController(E2eTestService e2eTestService) {
        this.e2eTestService = e2eTestService;
    }

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<E2eTestResponse>> run(@RequestBody E2eTestRequest req) {
        E2eTestResponse result = e2eTestService.runE2eTest(req);
        return ResponseEntity.ok(ApiResponse.ok("E2E smoke test completed successfully", result));
    }
}
