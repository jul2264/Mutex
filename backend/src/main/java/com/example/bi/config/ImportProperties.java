package com.example.bi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@ConfigurationProperties(prefix = "app.import")
public class ImportProperties {

    private long maxFileSizeBytes = 10 * 1024 * 1024; // 10 MB
    private int maxRows = 100_000;
    private int maxColumns = 50;
    private int batchSize = 1_000;
    private int timeoutSeconds = 120;
    private int maxStoredIssues = 10_000;
    private String tempDir;

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    public int getMaxColumns() {
        return maxColumns;
    }

    public void setMaxColumns(int maxColumns) {
        this.maxColumns = maxColumns;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxStoredIssues() {
        return maxStoredIssues;
    }

    public void setMaxStoredIssues(int maxStoredIssues) {
        this.maxStoredIssues = maxStoredIssues;
    }

    public String getTempDir() {
        if (tempDir == null || tempDir.isBlank()) {
            return System.getProperty("java.io.tmpdir") + File.separator + "mutex-imports";
        }
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }
}

