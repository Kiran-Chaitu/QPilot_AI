package com.testforge.backend.analysis.entity;

import com.testforge.backend.ai.dto.AgentType;
import com.testforge.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Audit trail of every call made to an AI provider ("ai_requests" in the
 * hackathon data-model sketch): which agent, which provider, how long it
 * took, and whether it succeeded.
 */
@Entity
@Table(name = "ai_requests")
@Getter
@Setter
@NoArgsConstructor
public class AiRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_run_id")
    private AnalysisRun analysisRun;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentType agentType;

    @Column(nullable = false)
    private String providerName;

    private long latencyMs;

    @Column(nullable = false)
    private boolean success;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
