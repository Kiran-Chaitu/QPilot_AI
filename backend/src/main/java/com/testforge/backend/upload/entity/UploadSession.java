package com.testforge.backend.upload.entity;

import com.testforge.backend.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Tracks one client-initiated chunked upload from init through assembly. The chunk part-files on
 * disk (see FileStorageService) are the source of truth for *which* chunks have arrived; this
 * entity tracks session-level metadata, ownership and lifecycle status.
 */
@Entity
@Table(name = "upload_sessions")
@Getter
@Setter
@NoArgsConstructor
public class UploadSession {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    private long totalSizeBytes;

    @Column(nullable = false)
    private long chunkSizeBytes;

    @Column(nullable = false)
    private int totalChunks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadSessionStatus status = UploadSessionStatus.INITIATED;

    /** Carried through from the init request so /complete can create the Project without re-asking the client. */
    private String projectName;

    @Column(length = 1000)
    private String description;

    /** Set once the archive is assembled and a Project row has been created for it. */
    private Long resultProjectId;

    @Column(length = 2000)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    @Column(nullable = false)
    private Instant expiresAt;

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
