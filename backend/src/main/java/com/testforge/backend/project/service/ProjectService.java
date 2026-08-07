package com.testforge.backend.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.backend.auth.entity.User;
import com.testforge.backend.common.exception.BadRequestException;
import com.testforge.backend.common.exception.ResourceNotFoundException;
import com.testforge.backend.common.storage.FileStorageService;
import com.testforge.backend.project.dto.ProjectDetailResponse;
import com.testforge.backend.project.dto.ProjectResponse;
import com.testforge.backend.project.dto.ProjectStructureSummary;
import com.testforge.backend.project.entity.Project;
import com.testforge.backend.project.entity.ProjectSourceType;
import com.testforge.backend.project.entity.ProjectStatus;
import com.testforge.backend.project.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1900;

    private final ProjectRepository projectRepository;
    private final FileStorageService fileStorageService;
    private final ProjectMetadataAnalyzer metadataAnalyzer;
    private final ObjectMapper objectMapper;

    public ProjectService(ProjectRepository projectRepository, FileStorageService fileStorageService,
                           ProjectMetadataAnalyzer metadataAnalyzer, ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.fileStorageService = fileStorageService;
        this.metadataAnalyzer = metadataAnalyzer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProjectResponse uploadZip(User owner, String name, String description, MultipartFile zipFile) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new BadRequestException("A non-empty ZIP file is required");
        }
        String originalName = zipFile.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".zip")) {
            throw new BadRequestException("Only .zip archives are supported for project upload");
        }

        Project project = createProjectRecord(owner, name, description, originalName);
        Path zipPath = fileStorageService.saveUpload(project.getId(), zipFile, "source");
        project.setOriginalArchivePath(zipPath.toString());

        // Synchronous path (small/typical files): exceptions here propagate straight to the caller,
        // the surrounding @Transactional rolls back, and GlobalExceptionHandler turns them into a
        // clean 4xx/5xx response — matching the pre-chunked-upload behavior exactly.
        extractAndAnalyze(project, zipPath);

        return toResponse(projectRepository.save(project));
    }

    /**
     * Creates the Project row for a chunked upload before its archive has been assembled on disk,
     * in status EXTRACTING, so the caller has a stable numeric ID to assemble chunks under (the
     * final archive path is attached afterward via {@link #attachArchivePath}). Actual
     * extraction/analysis is kicked off separately via {@link #processUploadedArchiveAsync} so the
     * HTTP request that triggered assembly is never blocked by extraction of a potentially huge archive.
     */
    @Transactional
    public Project createProjectForAssembledArchive(User owner, String name, String description, String originalFileName) {
        Project project = createProjectRecord(owner, name, description, originalFileName);
        project.setStatus(ProjectStatus.EXTRACTING);
        return projectRepository.save(project);
    }

    /** Records where the assembled archive ended up on disk once chunk assembly completes. */
    @Transactional
    public void attachArchivePath(Long projectId, Path zipPath) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        project.setOriginalArchivePath(zipPath.toString());
        projectRepository.save(project);
    }

    /**
     * Background extraction + structure analysis for a chunked-upload project. Runs off the
     * request thread on a dedicated executor (see AsyncConfig); any failure here is caught and
     * persisted as ProjectStatus.FAILED + a processingError message rather than being thrown into
     * the void, since there's no HTTP response left to report it through.
     */
    @Async("uploadProcessingExecutor")
    public void processUploadedArchiveAsync(Long projectId, Path zipPath) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            log.warn("processUploadedArchiveAsync: project {} no longer exists; skipping", projectId);
            return;
        }
        try {
            extractAndAnalyze(project, zipPath);
            projectRepository.save(project);
            log.info("Background processing completed for project {}", projectId);
        } catch (Exception ex) {
            log.error("Background processing failed for project {}: {}", projectId, ex.getMessage(), ex);
            project.setStatus(ProjectStatus.FAILED);
            project.setProcessingError(truncate(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
            projectRepository.save(project);
        }
    }

    /** Shared by both the synchronous and background upload paths: extract, analyze, mark ready. */
    private void extractAndAnalyze(Project project, Path zipPath) {
        Path extractedRoot = fileStorageService.extractZip(project.getId(), zipPath);
        Path effectiveRoot = resolveEffectiveRoot(extractedRoot);
        project.setStoragePath(effectiveRoot.toString());
        analyzeAndPersist(project, effectiveRoot);
        project.setStatus(ProjectStatus.UPLOADED);
        project.setProcessingError(null);
    }

    private Project createProjectRecord(User owner, String name, String description, String originalFileName) {
        Project project = new Project();
        project.setOwner(owner);
        project.setName(name != null && !name.isBlank() ? name : stripExtension(originalFileName));
        project.setDescription(description);
        project.setSourceType(ProjectSourceType.ZIP);
        project.setStatus(ProjectStatus.UPLOADED);
        return projectRepository.save(project);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_MESSAGE_LENGTH ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "…" : message;
    }

    @Transactional
    public ProjectResponse attachSwagger(User owner, Long projectId, MultipartFile swaggerFile) {
        Project project = getOwnedProject(owner, projectId);
        if (swaggerFile == null || swaggerFile.isEmpty()) {
            throw new BadRequestException("A non-empty Swagger/OpenAPI file is required");
        }
        Path path = fileStorageService.saveUpload(projectId, swaggerFile, "swagger");
        project.setSwaggerFilePath(path.toString());
        return toResponse(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listMine(User owner) {
        return projectRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse getDetail(User owner, Long projectId) {
        Project project = getOwnedProject(owner, projectId);
        ProjectStructureSummary summary = readStructureSummary(project);
        return new ProjectDetailResponse(toResponse(project), summary);
    }

    @Transactional(readOnly = true)
    public Project getOwnedProject(User owner, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        if (!project.getOwner().getId().equals(owner.getId())) {
            throw new ResourceNotFoundException("Project not found: " + projectId);
        }
        return project;
    }

    @Transactional
    public void updateStatus(Project project, ProjectStatus status) {
        project.setStatus(status);
        projectRepository.save(project);
    }

    public ProjectStructureSummary readStructureSummary(Project project) {
        if (project.getStructureSummaryJson() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(project.getStructureSummaryJson(), ProjectStructureSummary.class);
        } catch (IOException e) {
            return null;
        }
    }

    private void analyzeAndPersist(Project project, Path extractedRoot) {
        ProjectStructureSummary summary = metadataAnalyzer.analyze(extractedRoot);
        project.setFileCount(summary.totalFiles());
        project.setPrimaryLanguage(summary.primaryLanguage());
        try {
            project.setStructureSummaryJson(objectMapper.writeValueAsString(summary));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize project structure summary", e);
        }
    }

    /**
     * Many uploaded ZIPs contain a single top-level wrapper folder
     * (e.g. "my-project-main/"). If so, treat that folder as the real root
     * so file paths and endpoint discovery read naturally.
     */
    private Path resolveEffectiveRoot(Path extractedRoot) {
        try (var stream = java.nio.file.Files.list(extractedRoot)) {
            List<Path> entries = stream.toList();
            if (entries.size() == 1 && java.nio.file.Files.isDirectory(entries.get(0))) {
                return entries.get(0);
            }
        } catch (IOException ignored) {
        }
        return extractedRoot;
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }

    private ProjectResponse toResponse(Project p) {
        return new ProjectResponse(
                p.getId(), p.getName(), p.getDescription(), p.getSourceType(), p.getRepoUrl(),
                p.getPrimaryLanguage(), p.getFileCount(), p.getStatus(), p.getSwaggerFilePath() != null,
                p.getProcessingError(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
