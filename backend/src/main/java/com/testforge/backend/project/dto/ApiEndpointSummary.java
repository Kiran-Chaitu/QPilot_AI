package com.testforge.backend.project.dto;

public record ApiEndpointSummary(String httpMethod, String path, String sourceFile, String handlerName) {
}
