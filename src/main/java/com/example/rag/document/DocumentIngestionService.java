package com.example.rag.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.rag.config.DocumentIngestionProperties;
import com.example.rag.vector.VectorStorePersistenceService;

@Service
public class DocumentIngestionService {

    private final VectorStorePersistenceService vectorStore;

    private final DocumentIngestionProperties properties;

    private final EmbeddedFileManifestService manifestService;

    public DocumentIngestionService(
            VectorStorePersistenceService vectorStore,
            DocumentIngestionProperties properties,
            EmbeddedFileManifestService manifestService) {
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.manifestService = manifestService;
    }

    public IngestionResult ingest(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        var text = extractText(file);
        if (text.isBlank()) {
            throw new IllegalArgumentException("Uploaded file has no readable text content");
        }

        var uploadId = UUID.randomUUID().toString();
        var uploadedAt = Instant.now();
        var filename = safeFilename(file);
        var contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        return ingestSources(
                filename,
                contentType,
                file.getSize(),
                uploadedAt,
                List.of(new TextSource(filename, text, Map.of())));
    }

    public IngestionResult ingestSources(
            String manifestName,
            String contentType,
            long size,
            Instant uploadedAt,
            List<TextSource> sources) {
        var uploadId = UUID.randomUUID().toString();
        var documents = new ArrayList<Document>();
        var chunkIndex = 0;

        for (var source : sources) {
            var chunks = chunk(source.text());
            for (var sourceChunkIndex = 0; sourceChunkIndex < chunks.size(); sourceChunkIndex++) {
                var metadata = new HashMap<String, Object>();
                metadata.put("uploadId", uploadId);
                metadata.put("filename", source.filename());
                metadata.put("contentType", contentType);
                metadata.put("chunkIndex", chunkIndex);
                metadata.put("sourceChunkIndex", sourceChunkIndex);
                metadata.put("sourceChunkCount", chunks.size());
                metadata.put("uploadedAt", uploadedAt.toString());
                metadata.putAll(source.metadata());

                documents.add(Document.builder()
                        .id(uploadId + "-" + chunkIndex)
                        .text(chunks.get(sourceChunkIndex))
                        .metadata(metadata)
                        .build());
                chunkIndex++;
            }
        }

        if (documents.isEmpty()) {
            throw new IllegalArgumentException("No readable text content found");
        }

        var chunkCount = documents.size();
        for (var document : documents) {
            document.getMetadata().put("chunkCount", chunkCount);
        }

        this.vectorStore.add(documents);
        this.manifestService.record(new EmbeddedFileManifestService.EmbeddedFile(
                uploadId,
                manifestName,
                contentType,
                chunkCount,
                size,
                uploadedAt));

        return new IngestionResult(uploadId, manifestName, chunkCount, size, uploadedAt);
    }

    private String extractText(MultipartFile file) throws IOException {
        return new String(file.getBytes(), StandardCharsets.UTF_8)
                .replace("\u0000", "")
                .trim();
    }

    private List<String> chunk(String text) {
        var normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        var chunks = new ArrayList<String>();
        var size = this.properties.chunkSize();
        var step = Math.max(1, size - this.properties.chunkOverlap());

        for (int start = 0; start < normalized.length(); start += step) {
            var end = Math.min(normalized.length(), start + size);
            var chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end == normalized.length()) {
                break;
            }
        }

        return chunks;
    }

    private String safeFilename(MultipartFile file) {
        var filename = file.getOriginalFilename();
        return filename == null || filename.isBlank() ? "upload.txt" : filename;
    }

    public List<EmbeddedFileManifestService.EmbeddedFile> listEmbeddedFiles() {
        return this.manifestService.list();
    }

    public DeletionResult deleteEmbeddedFile(String uploadId) {
        var file = this.manifestService.remove(uploadId);
        var documentIds = new ArrayList<String>();
        for (int i = 0; i < file.chunks(); i++) {
            documentIds.add(uploadId + "-" + i);
        }

        this.vectorStore.delete(documentIds);
        return new DeletionResult(uploadId, file.filename(), documentIds.size());
    }

    public record IngestionResult(String uploadId, String filename, int chunks, long size, Instant uploadedAt) {
    }

    public record DeletionResult(String uploadId, String filename, int deletedChunks) {
    }

    public record TextSource(String filename, String text, Map<String, Object> metadata) {
    }
}
