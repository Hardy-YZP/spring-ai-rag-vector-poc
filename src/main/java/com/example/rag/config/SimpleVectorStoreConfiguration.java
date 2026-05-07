package com.example.rag.config;

import java.io.IOException;
import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimpleVectorStoreConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SimpleVectorStoreConfiguration.class);

    @Bean
    SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel, VectorStoreProperties properties)
            throws IOException {
        var vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        var file = properties.file().toFile();

        if (Files.exists(properties.file())) {
            vectorStore.load(file);
            log.info("Loaded SimpleVectorStore from {}", properties.file().toAbsolutePath());
        }
        else {
            var parent = properties.file().toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            log.info("SimpleVectorStore persistence file does not exist yet: {}", properties.file().toAbsolutePath());
        }

        return vectorStore;
    }
}
