package com.testforge.backend.swaggerspec.controller;

import com.testforge.backend.auth.entity.User;
import com.testforge.backend.common.dto.ApiResponse;
import com.testforge.backend.common.exception.BadRequestException;
import com.testforge.backend.project.entity.Project;
import com.testforge.backend.project.service.ProjectService;
import com.testforge.backend.swaggerspec.dto.SwaggerParseResult;
import com.testforge.backend.swaggerspec.service.SwaggerParsingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{id}/swagger")
@Tag(name = "Swagger / OpenAPI")
@SecurityRequirement(name = "bearerAuth")
public class SwaggerController {

    private final ProjectService projectService;
    private final SwaggerParsingService swaggerParsingService;

    public SwaggerController(ProjectService projectService, SwaggerParsingService swaggerParsingService) {
        this.projectService = projectService;
        this.swaggerParsingService = swaggerParsingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SwaggerParseResult>> getParsedSpec(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Project project = projectService.getOwnedProject(user, id);
        if (project.getSwaggerFilePath() == null) {
            throw new BadRequestException("No Swagger/OpenAPI spec has been uploaded for this project yet");
        }
        return ResponseEntity.ok(ApiResponse.ok(swaggerParsingService.parse(project.getSwaggerFilePath())));
    }
}
