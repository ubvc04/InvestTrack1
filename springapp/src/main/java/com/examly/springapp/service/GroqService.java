package com.examly.springapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groq API client (OpenAI-compatible chat completions) used for natural-language
 * criteria interpretation and connectivity health checks.
 *
 * <h2>Security</h2>
 * The API key is sent in the {@code Authorization: Bearer} header — never as a URL
 * query parameter — so the key cannot leak through logged URLs, exception messages or
 * access logs. The key is never returned from this service or logged.
 *
 * <h2>Embeddings</h2>
 * The configured Groq account exposes no embedding model, so {@link #embed(String)}
 * deliberately returns {@code null} and {@link #embedBatch} returns an empty list.
 * Callers (such as the legacy lexical search) already treat that as "unavailable"
 * and fall back to deterministic local ranking — no semantic ranking is ever faked.
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
public class GroqService {

    private static final Logger logger = LoggerFactory.getLogger(GroqService.class);

    /** Sanitized error categories shared with the health check and diagnostics panel. */
    public static final String CAT_OK = "OK";
    public static final String CAT_DISABLED = "DISABLED";
    public static final String CAT_AUTH = "AUTH_ERROR";
    public static final String CAT_QUOTA = "QUOTA_EXCEEDED";
    public static final String CAT_MODEL = "MODEL_NOT_FOUND";
    public static final String CAT_TIMEOUT = "TIMEOUT";
    public static final String CAT_NETWORK = "NETWORK_ERROR";
    public static final String CAT_INVALID = "INVALID_RESPONSE";

    private static final String API_BASE = "https://api.groq.com/openai/v1/";

    private final RestTemplate restTemplate;

    @Value("${groq.api.key:}")
    private String apiKey;

    @Value("${groq.model:openai/gpt-oss-120b}")
    private String generationModel;

    public GroqService() {
        // Bounded timeouts: a hung Groq call must not hang the search request.
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

    /**
     * Semantic embedding lookup. The configured Groq account has no embedding model,
     * so this always returns {@code null}: callers must use their deterministic
     * local fallback rather than any fabricated vector.
     */
    public float[] embed(String text) {
        if (isEnabled()) {
            logger.debug("Groq account exposes no embedding model; semantic ranking unavailable");
        }
        return null;
    }

    /** Batch variant of {@link #embed(String)}; always empty for the same reason. */
    public List<float[]> embedBatch(List<String> texts) {
        return List.of();
    }

    /**
     * Simple generation call kept for compatibility. Returns the generated text or an
     * empty string when Groq is disabled or the call failed — use
     * {@link #generateDetailed(String, boolean)} when the error category is needed.
     */
    public String generate(String prompt) {
        return generateDetailed(prompt, false).text();
    }

    /**
     * Generation call with a structured outcome.
     *
     * @param fast when {@code true} a small, cheap, fast reply is requested (used for
     *             health probes and criteria JSON extraction)
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
        requestBody.put("model", model);
        requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        requestBody.put("temperature", 0.2);
        requestBody.put("max_tokens", fast ? 1024 : 8192);

        try {
            Map<?, ?> response = postWithHeaderAuth(
                    API_BASE + "chat/completions", requestBody, Map.class);
            long latency = System.currentTimeMillis() - started;

            if (response == null || !(response.get("choices") instanceof List<?> choices)
                    || choices.isEmpty()) {
                logger.warn("Groq returned no choices (model={}, latency={}ms)", model, latency);
                return new GenerationOutcome(false, "", CAT_INVALID, 200, latency, model);
            }

            Object first = choices.get(0);
            if (!(first instanceof Map<?, ?> choice)
                    || !(choice.get("message") instanceof Map<?, ?> message)) {
                logger.warn("Groq choice carried no message (model={}, latency={}ms)", model, latency);
                return new GenerationOutcome(false, "", CAT_INVALID, 200, latency, model);
            }

            Object text = message.get("content");
            String generated = text == null ? "" : text.toString();
            if (generated.isBlank()) {
                return new GenerationOutcome(false, "", CAT_INVALID, 200, latency, model);
            }
            return new GenerationOutcome(true, generated, CAT_OK, 200, latency, model);
        } catch (RestClientException exception) {
            long latency = System.currentTimeMillis() - started;
            ErrorCategory categorized = categorize(exception);
            logger.warn("Groq generation failed: category={}, status={}, model={}, latency={}ms",
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
                    0, 0, "No Groq API key is configured; AI search uses the deterministic "
                    + "fallback engine.", checkedAt);
        }

        GenerationOutcome outcome = generateDetailed(
                "Health check. Reply with exactly: PONG", true);
        if (outcome.success()) {
            return new HealthReport(true, model, true, "UP", CAT_OK, 200, outcome.latencyMs(),
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
                detail = "The Groq endpoint could not be reached from this server.";
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

    /** POSTs to the Groq API with the key in a header (never in the URL). */
    private <T> T postWithHeaderAuth(String url, Object body, Class<T> type) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + normalizedApiKey());
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
