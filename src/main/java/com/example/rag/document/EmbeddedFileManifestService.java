package com.example.rag.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.rag.config.DocumentIngestionProperties;

@Service
public class EmbeddedFileManifestService {

    private final DocumentIngestionProperties properties;

    public EmbeddedFileManifestService(DocumentIngestionProperties properties) {
        this.properties = properties;
    }

    public synchronized List<EmbeddedFile> list() {
        var file = this.properties.manifestFile();
        if (!Files.exists(file)) {
            return List.of();
        }

        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank())
                    .map(this::decode)
                    .sorted(Comparator.comparing(EmbeddedFile::uploadedAt).reversed())
                    .toList();
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to read embedded file manifest", ex);
        }
    }

    public synchronized void record(EmbeddedFile embeddedFile) {
        try {
            var file = this.properties.manifestFile();
            var parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            var records = new ArrayList<>(list());
            records.removeIf(existing -> existing.uploadId().equals(embeddedFile.uploadId()));
            records.add(embeddedFile);
            records.sort(Comparator.comparing(EmbeddedFile::uploadedAt));

            var lines = records.stream()
                    .map(this::encode)
                    .toList();
            Files.write(file, lines, StandardCharsets.UTF_8);
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to write embedded file manifest", ex);
        }
    }

    public synchronized EmbeddedFile remove(String uploadId) {
        var records = new ArrayList<>(list());
        var removed = records.stream()
                .filter(existing -> existing.uploadId().equals(uploadId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Embedded file not found: " + uploadId));

        records.removeIf(existing -> existing.uploadId().equals(uploadId));
        write(records);
        return removed;
    }

    private void write(List<EmbeddedFile> records) {
        try {
            var file = this.properties.manifestFile();
            var parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            records.sort(Comparator.comparing(EmbeddedFile::uploadedAt));
            var lines = records.stream()
                    .map(this::encode)
                    .toList();
            Files.write(file, lines, StandardCharsets.UTF_8);
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to write embedded file manifest", ex);
        }
    }

    private String encode(EmbeddedFile file) {
        return String.join("\t",
                file.uploadId(),
                encodeText(file.filename()),
                encodeText(file.contentType()),
                Integer.toString(file.chunks()),
                Long.toString(file.size()),
                file.uploadedAt().toString());
    }

    private EmbeddedFile decode(String line) {
        var parts = line.split("\t", -1);
        if (parts.length != 6) {
            throw new IllegalStateException("Invalid embedded file manifest line: " + line);
        }

        return new EmbeddedFile(
                parts[0],
                decodeText(parts[1]),
                decodeText(parts[2]),
                Integer.parseInt(parts[3]),
                Long.parseLong(parts[4]),
                Instant.parse(parts[5]));
    }

    private String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private String decodeText(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public record EmbeddedFile(
            String uploadId,
            String filename,
            String contentType,
            int chunks,
            long size,
            Instant uploadedAt
    ) {
    }
}
