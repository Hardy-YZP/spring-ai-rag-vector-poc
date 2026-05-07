package com.example.rag.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.vector-store")
public record VectorStoreProperties(
        Path file,
        boolean autoSaveOnAdd
) {
    public VectorStoreProperties {
        if (file == null) {
            file = Path.of("data", "simple-vector-store.json");
        }
    }
}
