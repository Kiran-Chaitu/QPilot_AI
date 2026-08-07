package com.testforge.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** Root directory (relative or absolute) all project artifacts live under. */
    private String rootDir = "./storage";
    private String uploadsDir = "uploads";
    private String extractedDir = "extracted";
    private String reportsDir = "reports";

    public String getRootDir() {
        return rootDir;
    }

    public void setRootDir(String rootDir) {
        this.rootDir = rootDir;
    }

    public String getUploadsDir() {
        return uploadsDir;
    }

    public void setUploadsDir(String uploadsDir) {
        this.uploadsDir = uploadsDir;
    }

    public String getExtractedDir() {
        return extractedDir;
    }

    public void setExtractedDir(String extractedDir) {
        this.extractedDir = extractedDir;
    }

    public String getReportsDir() {
        return reportsDir;
    }

    public void setReportsDir(String reportsDir) {
        this.reportsDir = reportsDir;
    }
}
