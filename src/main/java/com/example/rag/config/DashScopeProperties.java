package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.ai.dashscope")
public record DashScopeProperties(
        String apiKey,
        String baseUrl,
        Embedding embedding,
        Chat chat
) {
    public DashScopeProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        if (embedding == null) {
            embedding = new Embedding(new EmbeddingOptions("text-embedding-v3"));
        }
        if (chat == null) {
            chat = new Chat(new ChatOptions("qwen3-max", 0.7, 2048));
        }
    }

    public String embeddingModel() {
        return this.embedding.options.model;
    }

    public record Embedding(EmbeddingOptions options) {
        public Embedding {
            if (options == null) {
                options = new EmbeddingOptions("text-embedding-v3");
            }
        }
    }

    public record EmbeddingOptions(String model) {
        public EmbeddingOptions {
            if (model == null || model.isBlank()) {
                model = "text-embedding-v3";
            }
        }
    }

    public record Chat(ChatOptions options) {
        public Chat {
            if (options == null) {
                options = new ChatOptions("qwen3-max", 0.7, 2048);
            }
        }
    }

    public record ChatOptions(String model, double temperature, int maxTokens) {
        public ChatOptions {
            if (model == null || model.isBlank()) {
                model = "qwen3-max";
            }
            if (maxTokens <= 0) {
                maxTokens = 2048;
            }
        }
    }
}
