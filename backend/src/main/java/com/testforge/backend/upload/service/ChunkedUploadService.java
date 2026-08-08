package com.testforge.backend.upload.service;

import com.testforge.backend.auth.entity.User;
import com.testforge.backend.common.exception.BadRequestException;
import com.testforge.backend.common.exception.ResourceNotFoundException;
import com.testforge.backend.common.storage.FileStorageService;
import com.testforge.backend.config.UploadProperties;
import com.testforge.backend.project.dto.ProjectResponse;
import com.testforge.backend.project.entity.Project;
import com.testforge.backend.project.service.ProjectService;
import com.testforge.backend.upload.dto.UploadInitRequest;
import com.testforge.backend.upload.dto.UploadInitResponse;
import com.testforge.backend.upload.dto.UploadStatusResponse;
import com.testforge.backend.upload.entity.UploadSession;
import com.testforge.backend.upload.entity.UploadSessionStatus;
import com.testforge.backend.upload.repository.UploadSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates chunked/resumable project uploads: a client announces a file (init), streams it up
 * in many small chunks (each independently retryable and resumable), then asks the server to
 * assemble and process it. See {@link com.testforge.backend.common.storage.FileStorageService}
 * for the actual streaming disk I/O, and {@link ProjectService#processUploadedArchiveAsync} for
 * the background extraction/analysis kicked off once assembly completes.
 */
@Service
public class ChunkedUploadService {

    private final UploadSessionRepository uploadSessionRepository;
    private final FileStorageService fileStorageService;
    private final ProjectService projectService;
    private final UploadProperties uploadProperties;

    public ChunkedUploadService(UploadSessionRepository uploadSessionRepository, FileStorageService fileStorageService,
                                 ProjectService projectService, UploadProperties uploadProperties) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.fileStorageService = fileStorageService;
        this.projectService = projectService;
        this.uploadProperties = uploadProperties;
    }

    @Transactional
    public UploadInitResponse initSession(User owner, UploadInitRequest request) {
        if (!request.fileName().toLowerCase().endsWith(".zip")) {
            throw new BadRequestException("Only .zip archives are supported for project upload");
        }
        if (request.fileSizeBytes() > uploadProperties.getMaxTotalSizeBytes()) {
            throw new BadRequestException("\"" + request.fileName() + "\" is " + formatMb(request.fileSizeBytes())
                    + "MB, which exceeds the " + formatMb(uploadProperties.getMaxTotalSizeBytes())
                    + "MB upload limit. Please remove build artifacts/dependencies (e.g. node_modules, "
                    + ".dart_tool, build/) and re-zip just the source code.");
        }

        long chunkSize = uploadProperties.getChunkSizeBytes();
        int totalChunks = (int) Math.ceil(request.fileSizeBytes() / (double) chunkSize);
        if (totalChunks < 1) {
            totalChunks = 1;
        }

        UploadSession session = new UploadSession();
        session.setId(UUID.randomUUID().toString());
        session.setOwner(owner);
        session.setOriginalFileName(request.fileName());
        session.setTotalSizeBytes(request.fileSizeBytes());
        session.setChunkSizeBytes(chunkSize);
        session.setTotalChunks(totalChunks);
        session.setStatus(UploadSessionStatus.INITIATED);
        session.setProjectName(request.projectName());
        session.setDescription(request.description());
        session.setExpiresAt(Instant.now().plus(uploadProperties.getSessionTtlMinutes(), ChronoUnit.MINUTES));
        session = uploadSessionRepository.save(session);

        return new UploadInitResponse(session.getId(), chunkSize, totalChunks, session.getExpiresAt());
    }

    @Transactional
    public UploadStatusResponse receiveChunk(User owner, String sessionId, int chunkIndex, InputStream body) {
        UploadSession session = getOwnedActiveSession(owner, sessionId);
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new BadRequestException("Chunk index " + chunkIndex + " is out of range (0.." + (session.getTotalChunks() - 1) + ")");
        }
        // Allow a little slack over the nominal chunk size in case of client-side rounding, but
        // still bound it firmly so a chunk can never balloon into an unbounded disk write.
        long maxBytes = session.getChunkSizeBytes() + 1024;
        fileStorageService.writeChunk(sessionId, chunkIndex, body, maxBytes);

        if (session.getStatus() == UploadSessionStatus.INITIATED) {
            session.setStatus(UploadSessionStatus.UPLOADING);
            uploadSessionRepository.save(session);
        }
        return toStatusResponse(session);
    }

    @Transactional(readOnly = true)
    public UploadStatusResponse getStatus(User owner, String sessionId) {
        UploadSession session = getOwnedSession(owner, sessionId);
        return toStatusResponse(session);
    }

    /**
     * Assembles the archive and hands it off for background processing. Deliberately NOT wrapped
     * in a single outer @Transactional: each step below (create Project row, attach archive path,
     * update session) commits independently as soon as its own repository call returns, which
     * guarantees the Project row is durably visible in the database *before* the @Async background
     * job (running on a separate thread/connection) tries to load it. Wrapping this whole method in
     * one transaction would risk the async job racing ahead of the commit and finding nothing.
     */
    public ProjectResponse complete(User owner, String sessionId) {
        UploadSession session = getOwnedActiveSession(owner, sessionId);
        session.setStatus(UploadSessionStatus.ASSEMBLING);
        uploadSessionRepository.save(session);

        try {
            Project project = projectService.createProjectForAssembledArchive(owner, session.getProjectName(),
                    session.getDescription(), session.getOriginalFileName());
            Path assembled = fileStorageService.assembleChunks(sessionId, project.getId(), session.getOriginalFileName(),
                    session.getTotalChunks());
            projectService.attachArchivePath(project.getId(), assembled);

            session.setStatus(UploadSessionStatus.COMPLETED);
            session.setResultProjectId(project.getId());
            uploadSessionRepository.save(session);

            // Kick off extraction + structure analysis in the background, now that the Project row
            // is durably committed; the response below returns immediately with status EXTRACTING
            // and the frontend polls GET /api/projects/{id}.
            projectService.processUploadedArchiveAsync(project.getId(), assembled);

            return new ProjectResponse(project.getId(), project.getName(), project.getDescription(), project.getSourceType(),
                    project.getRepoUrl(), project.getTargetUrl(), project.getTargetApiUrl(),
                    project.getPrimaryLanguage(), project.getFileCount(), project.getStatus(),
                    project.getSwaggerFilePath() != null, project.getProcessingError(), project.getDiscoveryNotes(),
                    project.getCreatedAt(), project.getUpdatedAt());
        } catch (RuntimeException ex) {
            session.setStatus(UploadSessionStatus.FAILED);
            session.setErrorMessage(ex.getMessage());
            uploadSessionRepository.save(session);
            throw ex;
        }
    }

    @Transactional
    public void abort(User owner, String sessionId) {
        UploadSession session = getOwnedSession(owner, sessionId);
        fileStorageService.deleteChunkSession(sessionId);
        uploadSessionRepository.delete(session);
    }

    private UploadSession getOwnedSession(User owner, String sessionId) {
        return uploadSessionRepository.findByIdAndOwnerId(sessionId, owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Upload session not found: " + sessionId));
    }

    private UploadSession getOwnedActiveSession(User owner, String sessionId) {
        UploadSession session = getOwnedSession(owner, sessionId);
        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            throw new BadRequestException("Upload session " + sessionId + " has already been completed");
        }
        if (session.getStatus() == UploadSessionStatus.EXPIRED || session.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Upload session " + sessionId + " has expired; please start a new upload");
        }
        if (session.getStatus() == UploadSessionStatus.FAILED) {
            throw new BadRequestException("Upload session " + sessionId + " previously failed: " + session.getErrorMessage());
        }
        return session;
    }

    private UploadStatusResponse toStatusResponse(UploadSession session) {
        List<Integer> received = fileStorageService.listReceivedChunkIndices(session.getId());
        return new UploadStatusResponse(session.getId(), session.getStatus(), session.getTotalChunks(), received,
                session.getResultProjectId(), session.getErrorMessage());
    }

    private String formatMb(long bytes) {
        return String.format("%.1f", bytes / (1024.0 * 1024.0));
    }
}
