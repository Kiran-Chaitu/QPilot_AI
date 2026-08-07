package com.testforge.backend.ai;

import com.testforge.backend.ai.dto.AiConfigRequest;
import com.testforge.backend.ai.dto.AiConfigResponse;
import com.testforge.backend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/config")
@Tag(name = "AI Configuration")
@SecurityRequirement(name = "bearerAuth")
public class AiConfigController {

    private final AiClient aiClient;

    public AiConfigController(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AiConfigResponse>> getConfig() {
        return ResponseEntity.ok(ApiResponse.ok(aiClient.getConfigResponse()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AiConfigResponse>> updateConfig(@RequestBody AiConfigRequest req) {
        aiClient.updateConfig(req);
        return ResponseEntity.ok(ApiResponse.ok("AI provider configuration updated successfully", aiClient.getConfigResponse()));
    }
}
