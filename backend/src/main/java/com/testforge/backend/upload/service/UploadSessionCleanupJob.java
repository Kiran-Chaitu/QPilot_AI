package com.testforge.backend.upload.service;

import com.testforge.backend.common.storage.FileStorageService;
import com.testforge.backend.upload.entity.UploadSession;
import com.testforge.backend.upload.entity.UploadSessionStatus;
import com.testforge.backend.upload.repository.UploadSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Reclaims disk space and DB rows for upload sessions that were abandoned mid-upload (browser
 * closed, network dropped, client crashed) and never reached COMPLETED before their TTL expired.
 * Without this, every abandoned large-file upload attempt would leave its partial chunk files on
 * disk forever — part of the "robust error recovery" requirement for the upload pipeline.
 */
@Component
public class UploadSessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(UploadSessionCleanupJob.class);
    private static final List<UploadSessionStatus> TERMINAL_STATUSES =
            List.of(UploadSessionStatus.COMPLETED, UploadSessionStatus.EXPIRED);

    private final UploadSessionRepository uploadSessionRepository;
    private final FileStorageService fileStorageService;

    public UploadSessionCleanupJob(UploadSessionRepository uploadSessionRepository, FileStorageService fileStorageService) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.fileStorageService = fileStorageService;
    }

    @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT1M")
    @Transactional
    public void purgeExpiredSessions() {
        List<UploadSession> expired = uploadSessionRepository.findByStatusNotInAndExpiresAtBefore(
                TERMINAL_STATUSES, Instant.now());
        if (expired.isEmpty()) {
            return;
        }
        log.info("Purging {} expired/abandoned upload session(s)", expired.size());
        for (UploadSession session : expired) {
            fileStorageService.deleteChunkSession(session.getId());
            session.setStatus(UploadSessionStatus.EXPIRED);
            uploadSessionRepository.save(session);
        }
    }
}
