package com.testforge.backend.project.entity;

import com.testforge.backend.auth.entity.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "projects")
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

    /** Running Website URL for Synthetic Audit / Load Testing */
    private String targetUrl;

    /** Running API Base URL for API Testing / Load Testing */
    private String targetApiUrl;

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

    public Project() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ProjectSourceType getSourceType() { return sourceType; }
    public void setSourceType(ProjectSourceType sourceType) { this.sourceType = sourceType; }

    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }

    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }

    public String getTargetApiUrl() { return targetApiUrl; }
    public void setTargetApiUrl(String targetApiUrl) { this.targetApiUrl = targetApiUrl; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public String getOriginalArchivePath() { return originalArchivePath; }
    public void setOriginalArchivePath(String originalArchivePath) { this.originalArchivePath = originalArchivePath; }

    public String getSwaggerFilePath() { return swaggerFilePath; }
    public void setSwaggerFilePath(String swaggerFilePath) { this.swaggerFilePath = swaggerFilePath; }

    public String getPrimaryLanguage() { return primaryLanguage; }
    public void setPrimaryLanguage(String primaryLanguage) { this.primaryLanguage = primaryLanguage; }

    public Integer getFileCount() { return fileCount; }
    public void setFileCount(Integer fileCount) { this.fileCount = fileCount; }

    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }

    public String getStructureSummaryJson() { return structureSummaryJson; }
    public void setStructureSummaryJson(String structureSummaryJson) { this.structureSummaryJson = structureSummaryJson; }

    public String getProcessingError() { return processingError; }
    public void setProcessingError(String processingError) { this.processingError = processingError; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
