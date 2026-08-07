package com.testforge.backend.analysis.entity;

import com.testforge.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "risk_assessments")
@Getter
@Setter
@NoArgsConstructor
public class RiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false, unique = true)
    private AnalysisRun analysisRun;

    @Column(nullable = false)
    private int score;

    @ElementCollection
    @CollectionTable(name = "risk_assessment_reasons", joinColumns = @JoinColumn(name = "risk_assessment_id"))
    @Column(name = "reason", length = 1000)
    private List<String> reasons = new ArrayList<>();

    private int coverageEstimatePercent;

    @ElementCollection
    @CollectionTable(name = "risk_assessment_coverage_gaps", joinColumns = @JoinColumn(name = "risk_assessment_id"))
    @Column(name = "gap", length = 1000)
    private List<String> coverageGaps = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
