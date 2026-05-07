package com.example.rag.document;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.example.rag.config.DocumentIngestionProperties;

@Service
public class GitHubRepositoryIngestionService {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".java", ".kt", ".xml", ".yml", ".yaml", ".properties", ".md", ".txt", ".json",
            ".js", ".ts", ".tsx", ".jsx", ".html", ".css", ".scss", ".sql", ".sh", ".ps1",
            ".py", ".go", ".rs", ".c", ".h", ".cpp", ".hpp", ".cs", ".gradle", ".pom");

    private static final Set<String> TEXT_FILENAMES = Set.of(
            "dockerfile", "makefile", "readme", "license", ".gitignore", ".env.example");

    private final RestClient restClient;

    private final DocumentIngestionService ingestionService;

    private final DocumentIngestionProperties properties;

    public GitHubRepositoryIngestionService(
            RestClient.Builder restClientBuilder,
            DocumentIngestionService ingestionService,
            DocumentIngestionProperties properties) {
        this.restClient = restClientBuilder.build();
        this.ingestionService = ingestionService;
        this.properties = properties;
    }

    public DocumentIngestionService.IngestionResult ingest(GitHubRepositoryRequest request) {
        var repository = GitHubRepository.parse(request.repositoryUrl());
        var ref = StringUtils.hasText(request.ref()) ? request.ref().trim() : resolveDefaultBranch(repository, request.token());
        var archive = downloadArchive(repository, ref, request.token());
        var sources = extractSources(archive, repository, ref);

        if (sources.isEmpty()) {
            throw new IllegalArgumentException("No supported text/code files were found in the repository archive");
        }

        return this.ingestionService.ingestSources(
                "github:" + repository.owner() + "/" + repository.name() + "@" + ref,
                "application/vnd.github.repository",
                archive.length,
                Instant.now(),
                sources);
    }

    private String resolveDefaultBranch(GitHubRepository repository, String token) {
        try {
            var response = this.restClient.get()
                    .uri(URI.create("https://api.github.com/repos/%s/%s".formatted(repository.owner(), repository.name())))
                    .headers(headers -> {
                        headers.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
                        if (StringUtils.hasText(token)) {
                            headers.setBearerAuth(token.trim());
                        }
                    })
                    .retrieve()
                    .body(Map.class);

            if (response != null && StringUtils.hasText((String) response.get("default_branch"))) {
                return (String) response.get("default_branch");
            }
        }
        catch (RuntimeException ignored) {
            // Keep the UI ergonomic for public repos and older repos; an explicit ref still wins.
        }
        return "main";
    }

    private byte[] downloadArchive(GitHubRepository repository, String ref, String token) {
        var url = "https://codeload.github.com/%s/%s/zip/%s".formatted(
                repository.owner(),
                repository.name(),
                ref);

        return this.restClient.get()
                .uri(URI.create(url))
                .headers(headers -> {
                    headers.set(HttpHeaders.ACCEPT, "application/zip");
                    if (StringUtils.hasText(token)) {
                        headers.setBearerAuth(token.trim());
                    }
                })
                .retrieve()
                .body(byte[].class);
    }

    private List<DocumentIngestionService.TextSource> extractSources(
            byte[] archive,
            GitHubRepository repository,
            String ref) {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            var sources = new ArrayList<DocumentIngestionService.TextSource>();
            var entry = zip.getNextEntry();

            while (entry != null && sources.size() < this.properties.githubMaxFiles()) {
                if (!entry.isDirectory()) {
                    var path = stripRootFolder(entry.getName());
                    if (isSupportedTextPath(path)) {
                        var bytes = readEntry(zip, this.properties.githubMaxFileBytes());
                        if (bytes.length > 0 && bytes.length <= this.properties.githubMaxFileBytes() && !containsNul(bytes)) {
                            var text = new String(bytes, StandardCharsets.UTF_8).trim();
                            if (!text.isBlank()) {
                                sources.add(new DocumentIngestionService.TextSource(
                                        path,
                                        text,
                                        Map.of(
                                                "sourceType", "github",
                                                "repository", repository.owner() + "/" + repository.name(),
                                                "repositoryUrl", repository.url(),
                                                "repositoryRef", ref,
                                                "repositoryPath", path)));
                            }
                        }
                    }
                }
                zip.closeEntry();
                entry = zip.getNextEntry();
            }

            return sources;
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to read GitHub repository archive", ex);
        }
    }

    private byte[] readEntry(ZipInputStream zip, int maxBytes) throws IOException {
        var output = new ByteArrayOutputStream();
        var buffer = new byte[8192];
        var total = 0;
        var read = zip.read(buffer);
        while (read != -1) {
            total += read;
            if (total > maxBytes) {
                return new byte[maxBytes + 1];
            }
            output.write(buffer, 0, read);
            read = zip.read(buffer);
        }
        return output.toByteArray();
    }

    private boolean containsNul(byte[] bytes) {
        for (var value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    private String stripRootFolder(String path) {
        var slash = path.indexOf('/');
        return slash == -1 ? path : path.substring(slash + 1);
    }

    private boolean isSupportedTextPath(String path) {
        var normalized = path.replace('\\', '/');
        var name = normalized.substring(normalized.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (TEXT_FILENAMES.contains(name)) {
            return true;
        }
        return TEXT_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    public record GitHubRepositoryRequest(String repositoryUrl, String ref, String token) {
    }

    private record GitHubRepository(String owner, String name, String url) {
        static GitHubRepository parse(String value) {
            if (!StringUtils.hasText(value)) {
                throw new IllegalArgumentException("GitHub repository URL is required");
            }

            var normalized = value.trim()
                    .replace("git@github.com:", "https://github.com/")
                    .replaceAll("\\.git$", "");
            var uri = URI.create(normalized);
            var path = uri.getPath();
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("Invalid GitHub repository URL: " + value);
            }

            var parts = path.replaceFirst("^/", "").split("/");
            if (parts.length < 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
                throw new IllegalArgumentException("GitHub repository URL must look like https://github.com/owner/repo");
            }

            return new GitHubRepository(parts[0], parts[1], "https://github.com/" + parts[0] + "/" + parts[1]);
        }
    }
}
