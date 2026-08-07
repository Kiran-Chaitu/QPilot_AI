package com.testforge.backend.project.entity;

import com.testforge.backend.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectSourceType sourceType;

    private String repoUrl;

    /** Absolute path to the extracted project source on disk. */
    private String storagePath;

    /** Absolute path to the originally uploaded archive (kept for audit/re-extraction). */
    private String originalArchivePath;

    /** Absolute path to an uploaded Swagger/OpenAPI spec file, if any. */
    private String swaggerFilePath;

    private String primaryLanguage;

    private Integer fileCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.UPLOADED;

    /** JSON blob: language breakdown, dependencies, discovered API endpoints, top-level structure. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String structureSummaryJson;

    /** Populated when background extraction/analysis (see ProjectStatus.FAILED) fails, for display in the UI. */
    @Column(length = 2000)
    private String processingError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
