package com.example.rag.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-hash")
public class HashingEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 128;

    @Override
    public float[] embed(Document document) {
        return embedText(getEmbeddingContent(document));
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        var embeddings = new ArrayList<Embedding>();
        var inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(embedText(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private float[] embedText(String text) {
        var vector = new float[DIMENSIONS];
        var tokens = text == null ? List.<String>of() : List.of(text.toLowerCase().split("\\W+"));

        for (var token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            var digest = sha256(token);
            var index = Byte.toUnsignedInt(digest[0]) % DIMENSIONS;
            var sign = (digest[1] & 1) == 0 ? 1.0f : -1.0f;
            vector[index] += sign;
        }

        normalize(vector);
        return vector;
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JDK", ex);
        }
    }

    private void normalize(float[] vector) {
        float sum = 0.0f;
        for (float value : vector) {
            sum += value * value;
        }
        if (sum == 0.0f) {
            return;
        }
        var length = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / length;
        }
    }
}
