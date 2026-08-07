package com.testforge.backend.report.entity;

import com.testforge.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    private Long analysisRunId;

    @Column(nullable = false)
    private String storagePath;

    @Column(nullable = false, updatable = false)
    private Instant generatedAt = Instant.now();
}
