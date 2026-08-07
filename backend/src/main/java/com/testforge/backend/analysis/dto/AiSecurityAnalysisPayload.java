package com.testforge.backend.analysis.dto;

import java.util.List;

public record AiSecurityAnalysisPayload(List<Item> findings) {
    public record Item(String category, String severity, String description, String recommendation, String location) {
    }
}
