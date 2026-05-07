package com.example.rag.embedding;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.example.rag.config.DashScopeProperties;

@Component
@Profile("!local-hash")
public class DashScopeEmbeddingModel implements EmbeddingModel {

    private final DashScopeProperties properties;

    private final RestClient restClient;

    public DashScopeEmbeddingModel(DashScopeProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Override
    public float[] embed(Document document) {
        return embed(List.of(getEmbeddingContent(document))).getFirst();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        var vectors = embed(request.getInstructions());
        var embeddings = new ArrayList<Embedding>();
        for (int i = 0; i < vectors.size(); i++) {
            embeddings.add(new Embedding(vectors.get(i), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public List<float[]> embed(List<String> inputs) {
        if (!StringUtils.hasText(this.properties.apiKey())) {
            throw new IllegalStateException("DashScope API key is missing. Set DASHSCOPE_API_KEY first.");
        }

        var response = this.restClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(this.properties.apiKey()))
                .body(new EmbeddingsRequest(this.properties.embeddingModel(), inputs))
                .retrieve()
                .body(EmbeddingsResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("DashScope embedding response is empty");
        }

        return response.data().stream()
                .sorted((left, right) -> Integer.compare(left.index(), right.index()))
                .map(EmbeddingData::toFloatArray)
                .toList();
    }

    private record EmbeddingsRequest(String model, List<String> input) {
    }

    private record EmbeddingsResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(int index, List<Double> embedding) {
        float[] toFloatArray() {
            var vector = new float[this.embedding.size()];
            for (int i = 0; i < this.embedding.size(); i++) {
                vector[i] = this.embedding.get(i).floatValue();
            }
            return vector;
        }
    }
}
