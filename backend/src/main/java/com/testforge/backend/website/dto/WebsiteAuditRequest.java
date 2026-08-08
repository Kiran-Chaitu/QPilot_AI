package com.testforge.backend.website.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Audit request.
 *
 * @param checkLinks   whether to actually request the links found on the page. Off by default because
 *                     it multiplies the audit's outbound requests and its wall-clock time.
 * @param maxLinksToCheck upper bound on link requests, so a link-heavy page cannot turn an audit into
 *                        an unintended crawl of the target.
 */
public record WebsiteAuditRequest(
        @NotBlank(message = "Target URL is required")
        String targetUrl,

        boolean checkLinks,

        @Min(value = 1, message = "At least 1 link must be checked when link checking is enabled")
        @Max(value = 50, message = "At most 50 links can be checked in one audit")
        Integer maxLinksToCheck
) {
}
