package com.shk.generic;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Deterministic bag-of-words embedder: texts sharing words get similar vectors, so similarity
 * search behaves sensibly in tests without calling a real model. Records every text it embeds.
 */
public class StubEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 1536;

    private final List<String> embeddedTexts = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        if (failure != null) {
            throw failure;
        }
        List<Embedding> embeddings = new ArrayList<>();
        List<String> texts = request.getInstructions();
        for (int i = 0; i < texts.size(); i++) {
            embeddedTexts.add(texts.get(i));
            embeddings.add(new Embedding(vector(texts.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(getEmbeddingContent(document));
    }

    /** Mirrors OpenAiEmbeddingModel, which embeds the formatted content in EMBED mode. */
    @Override
    public String getEmbeddingContent(Document document) {
        return document.getFormattedContent(MetadataMode.EMBED);
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    public List<String> embeddedTexts() {
        return List.copyOf(embeddedTexts);
    }

    public void fail(RuntimeException e) {
        failure = e;
    }

    public void reset() {
        failure = null;
        embeddedTexts.clear();
    }

    private static float[] vector(String text) {
        float[] v = new float[DIMENSIONS];
        v[0] = 0.01f;   // never a zero vector (cosine distance would be undefined)
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!word.isEmpty()) {
                v[1 + Math.floorMod(word.hashCode(), DIMENSIONS - 1)] += 1f;
            }
        }
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        float n = (float) Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) {
            v[i] /= n;
        }
        return v;
    }
}
