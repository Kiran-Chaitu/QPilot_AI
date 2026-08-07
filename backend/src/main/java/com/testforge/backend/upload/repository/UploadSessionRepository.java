package com.testforge.backend.upload.repository;

import com.testforge.backend.upload.entity.UploadSession;
import com.testforge.backend.upload.entity.UploadSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UploadSessionRepository extends JpaRepository<UploadSession, String> {

    Optional<UploadSession> findByIdAndOwnerId(String id, Long ownerId);

    List<UploadSession> findByStatusNotInAndExpiresAtBefore(List<UploadSessionStatus> excludedStatuses, Instant cutoff);
}
