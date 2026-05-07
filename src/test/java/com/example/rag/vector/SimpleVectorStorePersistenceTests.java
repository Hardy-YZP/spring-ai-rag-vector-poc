package com.example.rag.vector;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import com.example.rag.embedding.HashingEmbeddingModel;

class SimpleVectorStorePersistenceTests {

    @Test
    void savesAndLoadsSimpleVectorStore(@TempDir Path tempDir) {
        var embeddingModel = new HashingEmbeddingModel();
        var file = tempDir.resolve("simple-vector-store.json").toFile();
        var originalStore = SimpleVectorStore.builder(embeddingModel).build();

        originalStore.add(List.of(Document.builder()
                .id("doc-1")
                .text("SimpleVectorStore can persist vectors with save and load.")
                .metadata(Map.of("source", "test"))
                .build()));
        originalStore.save(file);

        var loadedStore = SimpleVectorStore.builder(embeddingModel).build();
        loadedStore.load(file);

        var results = loadedStore.similaritySearch(SearchRequest.builder()
                .query("persist vectors")
                .topK(1)
                .similarityThresholdAll()
                .build());

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getId()).isEqualTo("doc-1");
        assertThat(results.getFirst().getMetadata()).containsEntry("source", "test");
    }

    @Test
    void deletesDocumentsById() {
        var embeddingModel = new HashingEmbeddingModel();
        var vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        vectorStore.add(List.of(Document.builder()
                .id("upload-1-0")
                .text("Delete this embedded file chunk.")
                .metadata(Map.of("uploadId", "upload-1"))
                .build()));

        assertThat(vectorStore.similaritySearch(SearchRequest.builder()
                .query("embedded file")
                .topK(1)
                .similarityThresholdAll()
                .build())).hasSize(1);

        vectorStore.delete(List.of("upload-1-0"));

        assertThat(vectorStore.similaritySearch(SearchRequest.builder()
                .query("embedded file")
                .topK(1)
                .similarityThresholdAll()
                .build())).isEmpty();
    }
}
