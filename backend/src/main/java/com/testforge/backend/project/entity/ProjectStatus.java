package com.testforge.backend.project.entity;

public enum ProjectStatus {
    /** Archive received; background extraction + structure analysis has not finished yet. */
    EXTRACTING,
    /** Extracted and structurally analyzed; ready for the AI analysis pipeline to run. */
    UPLOADED,
    ANALYZING,
    ANALYZED,
    FAILED
}
