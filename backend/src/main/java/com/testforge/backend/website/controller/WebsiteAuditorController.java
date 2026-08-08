package com.testforge.backend.website.controller;

import com.testforge.backend.common.dto.ApiResponse;
import com.testforge.backend.website.dto.WebsiteAuditRequest;
import com.testforge.backend.website.dto.WebsiteAuditResponse;
import com.testforge.backend.website.service.WebsiteAuditorService;
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
@RequestMapping("/api/website/audit")
@Tag(name = "Website Auditor")
@SecurityRequirement(name = "bearerAuth")
public class WebsiteAuditorController {

    private final WebsiteAuditorService websiteAuditorService;

    public WebsiteAuditorController(WebsiteAuditorService websiteAuditorService) {
        this.websiteAuditorService = websiteAuditorService;
    }

    /**
     * Audits a live URL.
     *
     * <p>Returns 200 even when the target turns out to be unreachable, because that is a successful
     * audit with a negative finding — the payload carries {@code reachable=false} and a specific
     * {@code failureReason}, which lets the UI render a real report instead of a generic error. A 4xx is
     * reserved for a genuinely bad request, such as a malformed URL.
     */
    @PostMapping
    @Operation(summary = "Audit a live website (headers, TLS, SEO, accessibility, links, response time)")
    public ResponseEntity<ApiResponse<WebsiteAuditResponse>> audit(@Valid @RequestBody WebsiteAuditRequest request) {
        WebsiteAuditResponse result = websiteAuditorService.audit(request);
        String message = result.reachable()
                ? "Audit completed. Target responded with HTTP " + result.responseCode()
                        + " in " + result.responseTimeMs() + "ms."
                : "Audit completed, but the target could not be reached. See failureReason for details.";
        return ResponseEntity.ok(ApiResponse.ok(message, result));
    }
}
