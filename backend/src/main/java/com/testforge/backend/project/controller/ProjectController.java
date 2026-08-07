package com.testforge.backend.project.controller;

import com.testforge.backend.auth.entity.User;
import com.testforge.backend.common.dto.ApiResponse;
import com.testforge.backend.project.dto.ProjectDetailResponse;
import com.testforge.backend.project.dto.ProjectResponse;
import com.testforge.backend.project.service.ProjectService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ProjectResponse>> upload(
            @AuthenticationPrincipal User user,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description) {
        ProjectResponse response = projectService.uploadZip(user, name, description, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Project uploaded and analyzed", response));
    }

    @PostMapping(value = "/{id}/swagger", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ProjectResponse>> uploadSwagger(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("Swagger/OpenAPI spec attached", projectService.attachSwagger(user, id, file)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> listMine(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.listMine(user)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> getDetail(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getDetail(user, id)));
    }
}
