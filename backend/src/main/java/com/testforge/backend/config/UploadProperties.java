package com.testforge.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the chunked/resumable upload pipeline (see com.testforge.backend.upload).
 * Chunk size is decided server-side (not client-supplied) so it can't be abused to bypass limits.
 */
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /** Size of each chunk the client should send, in bytes. Default 5MB. */
    private long chunkSizeBytes = 5L * 1024 * 1024;

    /** Maximum total size of an assembled archive, in bytes. Default 2GB. */
    private long maxTotalSizeBytes = 2L * 1024 * 1024 * 1024;

    /**
     * Maximum cumulative uncompressed bytes written during extraction, in bytes. Default 4GB.
     * Guards against zip bombs (a small archive that decompresses to an enormous size) independently
     * of the compressed archive size limit above, since build-artifact directories are now skipped
     * during extraction and don't count against this budget.
     */
    private long maxExtractedSizeBytes = 4L * 1024 * 1024 * 1024;

    /** Minutes an incomplete upload session is kept before scheduled cleanup purges it. Default 12h. */
    private int sessionTtlMinutes = 720;

    public long getChunkSizeBytes() {
        return chunkSizeBytes;
    }

    public void setChunkSizeBytes(long chunkSizeBytes) {
        this.chunkSizeBytes = chunkSizeBytes;
    }

    public long getMaxTotalSizeBytes() {
        return maxTotalSizeBytes;
    }

    public void setMaxTotalSizeBytes(long maxTotalSizeBytes) {
        this.maxTotalSizeBytes = maxTotalSizeBytes;
    }

    public long getMaxExtractedSizeBytes() {
        return maxExtractedSizeBytes;
    }

    public void setMaxExtractedSizeBytes(long maxExtractedSizeBytes) {
        this.maxExtractedSizeBytes = maxExtractedSizeBytes;
    }

    public int getSessionTtlMinutes() {
        return sessionTtlMinutes;
    }

    public void setSessionTtlMinutes(int sessionTtlMinutes) {
        this.sessionTtlMinutes = sessionTtlMinutes;
    }
}
