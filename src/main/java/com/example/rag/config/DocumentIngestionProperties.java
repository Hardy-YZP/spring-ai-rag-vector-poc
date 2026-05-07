package com.example.rag.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.document")
public record DocumentIngestionProperties(
        int chunkSize,
        int chunkOverlap,
        Path manifestFile,
        int githubMaxFiles,
        int githubMaxFileBytes
) {
    public DocumentIngestionProperties {
        if (chunkSize <= 0) {
            chunkSize = 900;
        }
        if (chunkOverlap < 0) {
            chunkOverlap = 120;
        }
        if (chunkOverlap >= chunkSize) {
            chunkOverlap = chunkSize / 4;
        }
        if (manifestFile == null) {
            manifestFile = Path.of("data", "embedded-files.tsv");
        }
        if (githubMaxFiles <= 0) {
            githubMaxFiles = 300;
        }
        if (githubMaxFileBytes <= 0) {
            githubMaxFileBytes = 1_000_000;
        }
    }
}
