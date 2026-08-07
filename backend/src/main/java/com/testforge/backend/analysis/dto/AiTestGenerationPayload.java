package com.testforge.backend.analysis.dto;

import java.util.List;

public record AiTestGenerationPayload(List<Item> tests) {
    public record Item(String type, String title, String targetName, String framework, String description, String code) {
    }
}
