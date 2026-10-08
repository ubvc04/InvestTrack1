package com.examly.springapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.embedding.model:gemini-embedding-001}")
    private String embeddingModel;

    @Value("${gemini.generation.model:gemini-2.5-flash}")
    private String generationModel;

    public boolean isEnabled() {
        return !normalizedApiKey().isBlank();
    }

    public float[] embed(String text) {
        if (!isEnabled() || text == null || text.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> content = new HashMap<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", text);
            content.put("parts", List.of(part));
            requestBody.put("content", content);

            Map<String, Object> response = restTemplate.postForObject(
                    "https://generativelanguage.googleapis.com/v1beta/models/" + embeddingModel.trim()
                            + ":embedContent?key=" + normalizedApiKey(),
                    requestBody,
                    Map.class
            );

            if (response == null || response.get("embedding") == null) {
                return null;
            }

            Object embeddingValue = ((Map<?, ?>) response.get("embedding")).get("values");
            if (!(embeddingValue instanceof List<?> values)) {
                return null;
            }

            float[] embedding = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                Object raw = values.get(i);
                if (raw instanceof Number number) {
                    embedding[i] = number.floatValue();
                } else {
                    return null;
                }
            }
            return embedding;
        } catch (RestClientException e) {
            logger.warn("Gemini embedding request failed; AI search will use fallback ranking", e);
            return null;
        }
    }

    public List<float[]> embedBatch(List<String> texts) {
        if (!isEnabled() || texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<Map<String, Object>> requests = new ArrayList<>();
            for (String text : texts) {
                requests.add(Map.of(
                        "model", "models/" + embeddingModel.trim(),
                        "content", Map.of("parts", List.of(Map.of("text", text)))
                ));
            }

            Map<String, Object> response = restTemplate.postForObject(
                    "https://generativelanguage.googleapis.com/v1beta/models/" + embeddingModel.trim()
                            + ":batchEmbedContents?key=" + normalizedApiKey(),
                    Map.of("requests", requests),
                    Map.class
            );

            Object embeddingsValue = response == null ? null : response.get("embeddings");
            if (!(embeddingsValue instanceof List<?> embeddings)) {
                return Collections.emptyList();
            }

            List<float[]> result = new ArrayList<>();
            for (Object embeddingValue : embeddings) {
                if (!(embeddingValue instanceof Map<?, ?> embeddingMap)
                        || !(embeddingMap.get("values") instanceof List<?> values)) {
                    return Collections.emptyList();
                }
                float[] vector = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    if (!(values.get(i) instanceof Number number)) {
                        return Collections.emptyList();
                    }
                    vector[i] = number.floatValue();
                }
                result.add(vector);
            }
            return result;
        } catch (RestClientException e) {
            logger.warn("Gemini batch embedding request failed; AI search will use fallback ranking", e);
            return Collections.emptyList();
        }
    }

    public String generate(String prompt) {
        if (!isEnabled() || prompt == null || prompt.isBlank()) {
            return "";
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);
            contents.add(Map.of("parts", List.of(part)));
            requestBody.put("contents", contents);

            Map<String, Object> response = restTemplate.postForObject(
                    "https://generativelanguage.googleapis.com/v1beta/models/" + generationModel.trim()
                            + ":generateContent?key=" + normalizedApiKey(),
                    requestBody,
                    Map.class
            );

            if (response == null || response.get("candidates") == null) {
                return "";
            }

            List<?> candidates = (List<?>) response.get("candidates");
            if (candidates.isEmpty()) {
                return "";
            }

            Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
            Map<?, ?> contentMap = (Map<?, ?>) candidate.get("content");
            List<?> parts = (List<?>) contentMap.get("parts");
            if (parts == null || parts.isEmpty()) {
                return "";
            }

            Object text = ((Map<?, ?>) parts.get(0)).get("text");
            return text == null ? "" : text.toString();
        } catch (RestClientException e) {
            logger.warn("Gemini generation request failed", e);
            return "";
        }
    }

    private String normalizedApiKey() {
        if (apiKey == null) {
            return "";
        }

        String normalized = apiKey.trim();
        if (normalized.length() >= 2
                && ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'")))) {
            return normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }
}