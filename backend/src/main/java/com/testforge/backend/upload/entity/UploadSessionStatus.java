package com.testforge.backend.upload.entity;

public enum UploadSessionStatus {
    /** Session created; no chunks received yet. */
    INITIATED,
    /** At least one chunk received; still awaiting the rest. */
    UPLOADING,
    /** All chunks received; server is concatenating them into the final archive. */
    ASSEMBLING,
    /** Archive assembled and handed off to the (async) project-processing pipeline. */
    COMPLETED,
    /** Assembly or handoff failed; see UploadSession.errorMessage. */
    FAILED,
    /** Session exceeded its TTL before completion and was purged by the cleanup job. */
    EXPIRED
}
