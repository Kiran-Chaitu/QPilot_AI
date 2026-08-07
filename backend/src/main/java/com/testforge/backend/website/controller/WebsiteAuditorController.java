package com.testforge.backend.website.controller;

import com.testforge.backend.common.dto.ApiResponse;
import com.testforge.backend.website.dto.WebsiteAuditRequest;
import com.testforge.backend.website.dto.WebsiteAuditResponse;
import com.testforge.backend.website.service.WebsiteAuditorService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/website/audit")
@Tag(name = "Website Auditor")
@SecurityRequirement(name = "bearerAuth")
public class WebsiteAuditorController {

    private final WebsiteAuditorService websiteAuditorService;

    public WebsiteAuditorController(WebsiteAuditorService websiteAuditorService) {
        this.websiteAuditorService = websiteAuditorService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WebsiteAuditResponse>> audit(@RequestBody WebsiteAuditRequest req) {
        WebsiteAuditResponse result = websiteAuditorService.audit(req);
        return ResponseEntity.ok(ApiResponse.ok("Website audit completed successfully", result));
    }
}
