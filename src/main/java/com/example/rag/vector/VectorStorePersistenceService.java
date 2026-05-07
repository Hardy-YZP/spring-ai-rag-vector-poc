package com.example.rag.vector;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import jakarta.annotation.PreDestroy;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;

import com.example.rag.config.VectorStoreProperties;

@Service
public class VectorStorePersistenceService {

    private final SimpleVectorStore vectorStore;

    private final VectorStoreProperties properties;

    public VectorStorePersistenceService(SimpleVectorStore vectorStore, VectorStoreProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public synchronized void add(List<Document> documents) {
        this.vectorStore.add(documents);
        if (this.properties.autoSaveOnAdd()) {
            save();
        }
    }

    public synchronized void delete(List<String> documentIds) {
        if (documentIds.isEmpty()) {
            return;
        }
        this.vectorStore.delete(documentIds);
        if (this.properties.autoSaveOnAdd()) {
            save();
        }
    }

    public synchronized List<Document> search(String query, int topK) {
        var request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThresholdAll()
                .build();
        return this.vectorStore.similaritySearch(request);
    }

    public synchronized void save() {
        try {
            var file = this.properties.file();
            var parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.vectorStore.save(file.toFile());
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare SimpleVectorStore persistence file", ex);
        }
    }

    public synchronized void load() {
        var file = this.properties.file();
        if (!Files.exists(file)) {
            throw new IllegalStateException("SimpleVectorStore persistence file does not exist: " + file.toAbsolutePath());
        }
        this.vectorStore.load(file.toFile());
    }

    @PreDestroy
    public void saveOnShutdown() {
        save();
    }
}
