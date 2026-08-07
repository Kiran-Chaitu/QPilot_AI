package com.testforge.backend.analysis.entity;

import com.testforge.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "analysis_runs")
@Getter
@Setter
@NoArgsConstructor
public class AnalysisRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status = AnalysisStatus.RUNNING;

    /** Human-readable narrative summary produced by the Code Understanding agent. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String codeSummary;

    /** Full raw JSON returned by the Code Understanding agent (keyResponsibilities, notableObservations, ...). */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String codeSummaryJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private Instant startedAt = Instant.now();

    private Instant completedAt;
}
