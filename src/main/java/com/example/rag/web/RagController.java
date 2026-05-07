package com.example.rag.web;

import java.util.List;
import java.util.Map;

import java.io.IOException;

import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.rag.document.DocumentIngestionService;
import com.example.rag.document.EmbeddedFileManifestService;
import com.example.rag.document.GitHubRepositoryIngestionService;
import com.example.rag.vector.VectorStorePersistenceService;

@RestController
@RequestMapping("/rag")
public class RagController {

    private final VectorStorePersistenceService vectorStore;

    private final DocumentIngestionService ingestionService;

    private final GitHubRepositoryIngestionService gitHubRepositoryIngestionService;

    public RagController(
            VectorStorePersistenceService vectorStore,
            DocumentIngestionService ingestionService,
            GitHubRepositoryIngestionService gitHubRepositoryIngestionService) {
        this.vectorStore = vectorStore;
        this.ingestionService = ingestionService;
        this.gitHubRepositoryIngestionService = gitHubRepositoryIngestionService;
    }

    @PostMapping("/files")
    public DocumentIngestionService.IngestionResult addFile(@RequestParam("file") MultipartFile file) throws IOException {
        return this.ingestionService.ingest(file);
    }

    @PostMapping("/github/repositories")
    public DocumentIngestionService.IngestionResult addGitHubRepository(
            @org.springframework.web.bind.annotation.RequestBody GitHubRepositoryIngestionService.GitHubRepositoryRequest request) {
        return this.gitHubRepositoryIngestionService.ingest(request);
    }

    @GetMapping("/files")
    public List<EmbeddedFile> listFiles() {
        return this.ingestionService.listEmbeddedFiles().stream()
                .map(EmbeddedFile::from)
                .toList();
    }

    @DeleteMapping("/files/{uploadId}")
    public DocumentIngestionService.DeletionResult deleteFile(@PathVariable String uploadId) {
        return this.ingestionService.deleteEmbeddedFile(uploadId);
    }

    @GetMapping("/search")
    public List<SearchResult> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "3") int topK) {
        return this.vectorStore.search(query, topK).stream()
                .map(SearchResult::from)
                .toList();
    }

    @PostMapping("/vector-store/save")
    public Map<String, Object> save() {
        this.vectorStore.save();
        return Map.of("saved", true);
    }

    @PostMapping("/vector-store/load")
    public Map<String, Object> load() {
        this.vectorStore.load();
        return Map.of("loaded", true);
    }

    public record SearchResult(String id, String text, Map<String, Object> metadata, Double score) {
        static SearchResult from(Document document) {
            return new SearchResult(document.getId(), document.getText(), document.getMetadata(), document.getScore());
        }
    }

    public record EmbeddedFile(
            String uploadId,
            String filename,
            String contentType,
            int chunks,
            long size,
            String uploadedAt
    ) {
        static EmbeddedFile from(EmbeddedFileManifestService.EmbeddedFile file) {
            return new EmbeddedFile(
                    file.uploadId(),
                    file.filename(),
                    file.contentType(),
                    file.chunks(),
                    file.size(),
                    file.uploadedAt().toString());
        }
    }
}
