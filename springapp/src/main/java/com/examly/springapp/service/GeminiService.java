package com.examly.springapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * Google Gemini API client (REST, {@code v1beta}) used for natural-language criteria
 * interpretation, embeddings and connectivity health checks.
 *
 * <h2>Security</h2>
 * The API key is sent in the {@code x-goog-api-key} header — never as a URL query
 * parameter — so the key cannot leak through logged URLs, exception messages or
 * access logs. The key is never returned from this service or logged.
 *
 * <h2>Error handling</h2>
 * Every generation call returns a {@link GenerationOutcome} carrying a sanitized
 * error category instead of a raw exception: {@code DISABLED}, {@code AUTH_ERROR},
 * {@code QUOTA_EXCEEDED}, {@code MODEL_NOT_FOUND}, {@code TIMEOUT},
 * {@code NETWORK_ERROR}, {@code INVALID_RESPONSE} or {@code OK}. Callers decide
 * whether to fall back to deterministic logic; nothing is silently swallowed
 * without a category and latency measurement.
 */
@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    /** Sanitized error categories shared with the health check and diagnostics panel. */
    public static final String CAT_OK = "OK";
    public static final String CAT_DISABLED = "DISABLED";
    public static final String CAT_AUTH = "AUTH_ERROR";
    public static final String CAT_QUOTA = "QUOTA_EXCEEDED";
    public static final String CAT_MODEL = "MODEL_NOT_FOUND";
    public static final String CAT_TIMEOUT = "TIMEOUT";
    public static final String CAT_NETWORK = "NETWORK_ERROR";
    public static final String CAT_INVALID = "INVALID_RESPONSE";

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final RestTemplate restTemplate;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.embedding.model:gemini-embedding-001}")
    private String embeddingModel;

    @Value("${gemini.generation.model:gemini-2.5-flash}")
    private String generationModel;

    public GeminiService() {
        // Bounded timeouts: a hung Gemini call must not hang the search request.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(45_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** The outcome of one generation request, including a sanitized error category. */
    public record GenerationOutcome(boolean success, String text, String errorCategory,
                                    int httpStatus, long latencyMs, String model) {
        /** Transient conditions worth one bounded retry (never endless). */
        public boolean retryable() {
            return CAT_QUOTA.equals(errorCategory) || CAT_TIMEOUT.equals(errorCategory)
                    || CAT_NETWORK.equals(errorCategory);
        }
    }

    /** Connectivity probe result used by the health endpoint and diagnostics panel. */
    public record HealthReport(boolean configured, String model, boolean up, String status,
                               String errorCategory, int httpStatus, long latencyMs,
                               String detail, long checkedAtEpochMs) {
    }

    public boolean isEnabled() {
        return !normalizedApiKey().isBlank();
    }

    /** The configured generation model identifier (never the key). */
    public String getGenerationModel() {
        return generationModel == null ? "" : generationModel.trim();
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

            Map<String, Object> response = postWithHeaderAuth(
                    API_BASE + embeddingModel.trim() + ":embedContent", requestBody, Map.class);

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
            logger.warn("Gemini embedding request failed ({}); AI search will use fallback ranking",
                    categorize(e).category());
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

            Map<String, Object> response = postWithHeaderAuth(
                    API_BASE + embeddingModel.trim() + ":batchEmbedContents",
                    Map.of("requests", requests), Map.class);

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
            logger.warn("Gemini batch embedding request failed ({}); AI search will use fallback ranking",
                    categorize(e).category());
            return Collections.emptyList();
        }
    }

    /**
     * Simple generation call kept for compatibility. Returns the generated text or an
     * empty string when Gemini is disabled or the call failed — use
     * {@link #generateDetailed(String, boolean)} when the error category is needed.
     */
    public String generate(String prompt) {
        return generateDetailed(prompt, false).text();
    }

    /**
     * Generation call with a structured outcome.
     *
     * @param fast when {@code true} thinking is disabled for a small, cheap, fast reply
     *             (used for health probes and criteria JSON extraction)
     */
    public GenerationOutcome generateDetailed(String prompt, boolean fast) {
        long started = System.currentTimeMillis();
        String model = getGenerationModel();
        if (!isEnabled()) {
            return new GenerationOutcome(false, "", CAT_DISABLED, 0,
                    System.currentTimeMillis() - started, model);
        }
        if (prompt == null || prompt.isBlank()) {
            return new GenerationOutcome(false, "", CAT_INVALID, 0,
                    System.currentTimeMillis() - started, model);
        }

        Map<String, Object> requestBody = new HashMap<>();
        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        contents.add(Map.of("parts", List.of(part)));
        requestBody.put("contents", contents);

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.2);
        generationConfig.put("maxOutputTokens", fast ? 1024 : 8192);
        if (fast) {
            generationConfig.put("thinkingConfig", Map.of("thinkingBudget", 0));
        }
        requestBody.put("generationConfig", generationConfig);

        try {
            Map<String, Object> response = postWithHeaderAuth(
                    API_BASE + model + ":generateContent", requestBody, Map.class);
            long latency = System.currentTimeMillis() - started;

            if (response == null || !(response.get("candidates") instanceof List<?> candidates)
                    || candidates.isEmpty()) {
                logger.warn("Gemini returned no candidates (model={}, latency={}ms)", model, latency);
                return new GenerationOutcome(false, "", CAT_INVALID, 200, latency, model);
            }

            Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
            if (!(candidate.get("content") instanceof Map<?, ?> contentMap)
                    || !(contentMap.get("parts") instanceof List<?> parts)
                    || parts.isEmpty()) {
                logger.warn("Gemini candidate carried no text parts (model={}, latency={}ms)",
                        model, latency);
                return new GenerationOutcome(false, "", CAT_INVALID, 200, latency, model);
            }

            Object text = ((Map<?, ?>) parts.get(0)).get("text");
            String generated = text == null ? "" : text.toString();
            if (generated.isBlank()) {
                return new GenerationOutcome(false, "", CAT_INVALID, 200, latency, model);
            }
            return new GenerationOutcome(true, generated, CAT_OK, 200, latency, model);
        } catch (RestClientException exception) {
            long latency = System.currentTimeMillis() - started;
            ErrorCategory categorized = categorize(exception);
            logger.warn("Gemini generation failed: category={}, status={}, model={}, latency={}ms",
                    categorized.category(), categorized.httpStatus(), model, latency);
            return new GenerationOutcome(false, "", categorized.category(),
                    categorized.httpStatus(), latency, model);
        }
    }

    /**
     * Real connectivity probe: verifies the key is configured, sends one small, cheap
     * request to the configured generation model and measures the round trip. Never
     * reports UP merely because the server started or a key string exists.
     */
    public HealthReport healthCheck() {
        long checkedAt = System.currentTimeMillis();
        String model = getGenerationModel();
        if (!isEnabled()) {
            return new HealthReport(false, model, false, "NOT_CONFIGURED", CAT_DISABLED,
                    0, 0, "No Gemini API key is configured; AI search uses the deterministic "
                    + "fallback engine.", checkedAt);
        }

        GenerationOutcome outcome = generateDetailed(
                "Health check. Reply with exactly: PONG", true);
        if (outcome.success()) {
            return new HealthReport(true, model, true, "UP", CAT_OK, outcome.latencyMs() > 0
                    ? 200 : 200, outcome.latencyMs(),
                    "Model replied to a live test request.", checkedAt);
        }

        String status;
        String detail;
        switch (outcome.errorCategory()) {
            case CAT_AUTH -> {
                status = "AUTH_FAILED";
                detail = "The API key was rejected (authentication/permission failure).";
            }
            case CAT_QUOTA -> {
                status = "QUOTA_EXCEEDED";
                detail = "The API key's quota or rate limit was exceeded.";
            }
            case CAT_MODEL -> {
                status = "MODEL_UNAVAILABLE";
                detail = "The configured model is not accessible for this API key.";
            }
            case CAT_TIMEOUT -> {
                status = "TIMEOUT";
                detail = "The model did not answer within the configured timeout.";
            }
            case CAT_NETWORK -> {
                status = "NETWORK_ERROR";
                detail = "The Gemini endpoint could not be reached from this server.";
            }
            case "BAD_REQUEST" -> {
                status = "BAD_REQUEST";
                detail = "The API rejected the request format.";
            }
            default -> {
                status = "INVALID_RESPONSE";
                detail = "The model responded without usable text content.";
            }
        }
        return new HealthReport(true, model, false, status, outcome.errorCategory(),
                outcome.httpStatus(), outcome.latencyMs(), detail, checkedAt);
    }

    // ---------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------

    /** POSTs to the Gemini API with the key in a header (never in the URL). */
    private <T> T postWithHeaderAuth(String url, Object body, Class<T> type) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", normalizedApiKey());
        return restTemplate.postForObject(url, new org.springframework.http.HttpEntity<>(body, headers), type);
    }

    private record ErrorCategory(String category, int httpStatus) {
    }

    /** Maps a REST exception onto a sanitized category without exposing the key. */
    private ErrorCategory categorize(RestClientException exception) {
        Integer status = null;
        if (exception instanceof org.springframework.web.client.HttpStatusCodeException statusException) {
            status = statusException.getStatusCode().value();
        }
        if (status != null) {
            if (status == 401 || status == 403) {
                return new ErrorCategory(CAT_AUTH, status);
            }
            if (status == 429) {
                return new ErrorCategory(CAT_QUOTA, status);
            }
            if (status == 404) {
                return new ErrorCategory(CAT_MODEL, status);
            }
            if (status == 400) {
                return new ErrorCategory("BAD_REQUEST", status);
            }
            return new ErrorCategory(CAT_NETWORK, status);
        }
        if (exception instanceof ResourceAccessException
                && exception.getCause() instanceof SocketTimeoutException) {
            return new ErrorCategory(CAT_TIMEOUT, 0);
        }
        return new ErrorCategory(CAT_NETWORK, 0);
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
