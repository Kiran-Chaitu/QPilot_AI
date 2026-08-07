package com.testforge.backend.upload.controller;

import com.testforge.backend.auth.entity.User;
import com.testforge.backend.common.dto.ApiResponse;
import com.testforge.backend.project.dto.ProjectResponse;
import com.testforge.backend.upload.dto.UploadInitRequest;
import com.testforge.backend.upload.dto.UploadInitResponse;
import com.testforge.backend.upload.dto.UploadStatusResponse;
import com.testforge.backend.upload.service.ChunkedUploadService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * Chunked/resumable upload API, the redesigned primary path for large project archives:
 * <pre>
 * POST   /api/uploads/sessions                       -&gt; {sessionId, chunkSizeBytes, totalChunks}
 * PUT    /api/uploads/sessions/{id}/chunks/{index}    -&gt; upload one chunk (raw bytes, streamed to disk)
 * GET    /api/uploads/sessions/{id}                   -&gt; progress/status (for resumability + polling)
 * POST   /api/uploads/sessions/{id}/complete          -&gt; assemble + create Project (EXTRACTING) + kick off background processing
 * DELETE /api/uploads/sessions/{id}                   -&gt; abort and clean up
 * </pre>
 * Small archives can still use the simpler single-shot {@code POST /api/projects/upload} endpoint.
 */
@RestController
@RequestMapping("/api/uploads/sessions")
@Tag(name = "Chunked Uploads")
@SecurityRequirement(name = "bearerAuth")
public class UploadController {

    private final ChunkedUploadService uploadService;

    public UploadController(ChunkedUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UploadInitResponse>> init(
            @AuthenticationPrincipal User user, @Valid @RequestBody UploadInitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Upload session created", uploadService.initSession(user, request)));
    }

    @PutMapping("/{sessionId}/chunks/{index}")
    public ResponseEntity<ApiResponse<UploadStatusResponse>> putChunk(
            @AuthenticationPrincipal User user, @PathVariable String sessionId, @PathVariable int index,
            HttpServletRequest request) throws IOException {
        UploadStatusResponse status = uploadService.receiveChunk(user, sessionId, index, request.getInputStream());
        return ResponseEntity.ok(ApiResponse.ok("Chunk received", status));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<UploadStatusResponse>> status(
            @AuthenticationPrincipal User user, @PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(uploadService.getStatus(user, sessionId)));
    }

    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<ApiResponse<ProjectResponse>> complete(
            @AuthenticationPrincipal User user, @PathVariable String sessionId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Upload complete; processing started", uploadService.complete(user, sessionId)));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> abort(
            @AuthenticationPrincipal User user, @PathVariable String sessionId) {
        uploadService.abort(user, sessionId);
        return ResponseEntity.ok(ApiResponse.ok("Upload session aborted", null));
    }
}
