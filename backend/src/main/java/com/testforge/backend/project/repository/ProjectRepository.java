package com.testforge.backend.project.repository;

import com.testforge.backend.project.entity.Project;
import com.testforge.backend.project.entity.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    int countByOwnerId(Long ownerId);

    int countByOwnerIdAndStatus(Long ownerId, ProjectStatus status);
}
