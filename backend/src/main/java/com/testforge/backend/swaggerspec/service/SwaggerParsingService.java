package com.testforge.backend.swaggerspec.service;

import com.testforge.backend.common.exception.BadRequestException;
import com.testforge.backend.swaggerspec.dto.SwaggerEndpointSummary;
import com.testforge.backend.swaggerspec.dto.SwaggerParseResult;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses an uploaded Swagger/OpenAPI spec (JSON or YAML) into a flat list of
 * endpoint summaries used both for display and as AI prompt context for API
 * test generation.
 */
@Service
public class SwaggerParsingService {

    public SwaggerParseResult parse(String filePath) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        io.swagger.v3.parser.core.models.SwaggerParseResult result =
                new OpenAPIV3Parser().readLocation(filePath, null, options);
        OpenAPI openAPI = result.getOpenAPI();
        if (openAPI == null) {
            String errors = result.getMessages() != null ? String.join("; ", result.getMessages()) : "unknown error";
            throw new BadRequestException("Failed to parse Swagger/OpenAPI spec: " + errors);
        }

        List<SwaggerEndpointSummary> endpoints = new ArrayList<>();
        if (openAPI.getPaths() != null) {
            for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
                String path = pathEntry.getKey();
                Map<PathItem.HttpMethod, Operation> ops = pathEntry.getValue().readOperationsMap();
                for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : ops.entrySet()) {
                    Operation op = opEntry.getValue();
                    List<String> params = op.getParameters() == null ? List.of() :
                            op.getParameters().stream()
                                    .map(p -> p.getName() + " (" + p.getIn() + (Boolean.TRUE.equals(p.getRequired()) ? ", required" : "") + ")")
                                    .toList();
                    String requestBodyDesc = op.getRequestBody() != null && op.getRequestBody().getContent() != null
                            ? String.join(",", op.getRequestBody().getContent().keySet())
                            : null;
                    List<String> responseCodes = op.getResponses() == null ? List.of() : new ArrayList<>(op.getResponses().keySet());

                    endpoints.add(new SwaggerEndpointSummary(
                            opEntry.getKey().name(),
                            path,
                            op.getOperationId(),
                            op.getSummary(),
                            params,
                            requestBodyDesc,
                            responseCodes
                    ));
                }
            }
        }

        String title = openAPI.getInfo() != null ? openAPI.getInfo().getTitle() : "Untitled API";
        String version = openAPI.getInfo() != null ? openAPI.getInfo().getVersion() : "n/a";
        return new SwaggerParseResult(title, version, endpoints);
    }
}
