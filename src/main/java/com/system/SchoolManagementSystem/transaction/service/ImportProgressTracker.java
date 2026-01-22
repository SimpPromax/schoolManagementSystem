package com.system.SchoolManagementSystem.transaction.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class ImportProgressTracker {

    private final Map<String, ImportProgress> activeImports = new ConcurrentHashMap<>();

    @Data
    public static class ImportProgress {
        private final String importId;
        private final String fileName;
        private final long startTime;

        private AtomicInteger totalTransactions = new AtomicInteger(0);
        private AtomicInteger processedTransactions = new AtomicInteger(0);
        private AtomicInteger matchedTransactions = new AtomicInteger(0);
        private AtomicInteger failedTransactions = new AtomicInteger(0);

        private String currentStatus = "Initializing";
        private boolean isComplete = false;
        private String errorMessage;

        public ImportProgress(String importId, String fileName) {
            this.importId = importId;
            this.fileName = fileName;
            this.startTime = System.currentTimeMillis();
        }

        public double getProgressPercentage() {
            if (totalTransactions.get() == 0) return 0;
            return (processedTransactions.get() * 100.0) / totalTransactions.get();
        }

        public long getElapsedTime() {
            return System.currentTimeMillis() - startTime;
        }

        public int getRemainingTransactions() {
            return totalTransactions.get() - processedTransactions.get();
        }
    }

    public String startImport(String fileName) {
        String importId = "import_" + System.currentTimeMillis() + "_" +
                fileName.hashCode();

        ImportProgress progress = new ImportProgress(importId, fileName);
        activeImports.put(importId, progress);

        log.info("Started import {}: {}", importId, fileName);
        return importId;
    }

    public void updateProgress(String importId, int processed, int matched, int failed, String status) {
        ImportProgress progress = activeImports.get(importId);
        if (progress != null) {
            progress.processedTransactions.set(processed);
            progress.matchedTransactions.set(matched);
            progress.failedTransactions.set(failed);
            progress.currentStatus = status;

            if (processed % 1000 == 0) {
                log.debug("Import {}: {}/{} processed ({}% complete)",
                        importId, processed, progress.totalTransactions.get(),
                        String.format("%.1f", progress.getProgressPercentage()));
            }
        }
    }

    public void setTotalTransactions(String importId, int total) {
        ImportProgress progress = activeImports.get(importId);
        if (progress != null) {
            progress.totalTransactions.set(total);
        }
    }

    public void completeImport(String importId) {
        ImportProgress progress = activeImports.get(importId);
        if (progress != null) {
            progress.isComplete = true;
            progress.currentStatus = "Completed";

            log.info("Import {} completed: {} processed, {} matched, {} failed in {}ms",
                    importId,
                    progress.processedTransactions.get(),
                    progress.matchedTransactions.get(),
                    progress.failedTransactions.get(),
                    progress.getElapsedTime());

            // Remove after 5 minutes
            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            activeImports.remove(importId);
                        }
                    },
                    5 * 60 * 1000
            );
        }
    }

    public void failImport(String importId, String error) {
        ImportProgress progress = activeImports.get(importId);
        if (progress != null) {
            progress.isComplete = true;
            progress.currentStatus = "Failed";
            progress.errorMessage = error;

            log.error("Import {} failed: {}", importId, error);
        }
    }

    public ImportProgress getProgress(String importId) {
        return activeImports.get(importId);
    }

    public Map<String, ImportProgress> getActiveImports() {
        return new HashMap<>(activeImports);
    }
}