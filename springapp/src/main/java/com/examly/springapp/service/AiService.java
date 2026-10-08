package com.examly.springapp.service;

import com.examly.springapp.model.Investment;
import com.examly.springapp.dto.AiSearchResponseDTO;
import com.examly.springapp.dto.AiSearchResultDTO;
import com.examly.springapp.dto.ApiDtoMapper;
import com.examly.springapp.repository.InvestmentRepo;
import com.examly.springapp.exceptions.UnrelatedInvestmentQueryException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiService {

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    private final InvestmentRepo investmentRepository;
    private final GeminiService geminiService;
    private final double similarityThreshold;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Long, CachedEmbedding> embeddingCache = new ConcurrentHashMap<>();

    public AiService(InvestmentRepo investmentRepository, GeminiService geminiService,
                     @Value("${gemini.similarity.threshold:0.18}") double similarityThreshold) {
        this.investmentRepository = investmentRepository;
        this.geminiService = geminiService;
        this.similarityThreshold = similarityThreshold;
    }

    public List<Investment> searchInvestments(String query) {
        List<Investment> investments = investmentRepository.findAll();
        String normalizedQuery = query == null ? "" : query.trim();

        if (normalizedQuery.isBlank() || investments.isEmpty()) {
            return investments;
        }

        if (geminiService.isEnabled()) {
            try {
                float[] queryVector = geminiService.embed(normalizedQuery);
                if (queryVector != null) {
                    List<ScoredInvestment> scored = new ArrayList<>();
                    Map<Long, float[]> investmentVectors = getEmbeddingsForInvestments(investments);

                    for (Investment investment : investments) {
                        float[] investmentVector = investmentVectors.get(investment.getInvestmentId());
                        if (investmentVector != null) {
                            scored.add(new ScoredInvestment(investment, cosineSimilarity(queryVector, investmentVector)));
                        }
                    }

                    if (scored.size() == investments.size() && !scored.isEmpty()) {
                        scored.sort(scoreComparator());
                        List<Investment> ranked = scored.stream()
                                .filter(item -> item.score >= similarityThreshold)
                                .map(ScoredInvestment::investment)
                                .toList();
                        if (ranked.isEmpty()) {
                            try {
                                return lexicalFallback(investments, normalizedQuery);
                            } catch (UnrelatedInvestmentQueryException exception) {
                                throw new UnrelatedInvestmentQueryException(
                                        "Please ask about investments, markets, prices, asset types, or investment status.");
                            }
                        }
                        return ranked;
                    }
                }
            } catch (UnrelatedInvestmentQueryException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                logger.warn("Gemini semantic search failed; using lexical search fallback", exception);
            }
        }

        return lexicalFallback(investments, normalizedQuery);
    }

    public AiSearchResponseDTO searchDetailed(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        List<Investment> investments = investmentRepository.findAll();
        if (normalizedQuery.isBlank() || investments.isEmpty()) {
            return fallbackResponse(normalizedQuery, investments, false);
        }

        logger.info("AI search query: {}", normalizedQuery);
        logger.info("Total investments available: {}", investments.size());
        if (geminiService.isEnabled()) {
            logger.info("Sending all {} investments to Gemini", investments.size());
            logger.info("Gemini ranking requested");
            AiSearchResponseDTO analyzed = parseGeminiRanking(normalizedQuery, investments,
                    geminiService.generate(buildRankingPrompt(normalizedQuery, investments)));
            if (analyzed != null) {
                logger.info("Gemini ranking successfully applied");
                return analyzed;
            }
            logger.warn("Gemini ranking failed; using fallback investment ranking");
        } else {
            logger.warn("Gemini is disabled; using fallback investment ranking");
        }
        return fallbackResponse(normalizedQuery, fallbackCandidates(normalizedQuery, investments), false);
    }

    private String buildRankingPrompt(String query, List<Investment> investments) {
        StringBuilder prompt = new StringBuilder("""
                You are the primary ranking engine for an investment-management search. Return ONLY valid JSON:
                {"overview":"short explanation","results":[{"investmentId":1,"score":0.95,"reason":"evidence-based reason","matchedFactors":["stock"]}]}
                Analyze every supplied investment, but return only investments relevant enough to display.
                Sort returned results from highest relevance to lowest relevance. The returned order is the final frontend order.
                Return each selected investmentId at most once and never return an ID not supplied.
                Treat every meaningful user word as a possible search signal. Match exact words and clear
                semantic equivalents against name, symbol, type, market, assetClass, exchange, currency,
                status, and description. Use sector or industry wording in market, assetClass, type, and
                description to rank matching investments first. A query does not need a predefined category.
                Do not invent risk levels, performance, or other financial facts. If the data does not establish risk or long-term suitability, explain that limitation while ranking with available fields.
                If the query is unrelated to investments, return {"overview":"The query is unrelated to the available investment data.","results":[]}.
                USER QUERY:
                """).append(promptSafe(query)).append("\nALL INVESTMENTS:\n");

        for (Investment investment : investments) {
            prompt.append("{\"investmentId\":").append(investment.getInvestmentId())
                    .append(",\"name\":\"").append(promptSafe(investment.getName()))
                    .append("\",\"symbol\":\"").append(promptSafe(investment.getSymbol()))
                    .append("\",\"type\":\"").append(promptSafe(investment.getType()))
                    .append("\",\"market\":\"").append(promptSafe(investment.getMarket()))
                    .append("\",\"assetClass\":\"").append(promptSafe(investment.getAssetClass()))
                    .append("\",\"currency\":\"").append(promptSafe(investment.getCurrency()))
                    .append("\",\"exchange\":\"").append(promptSafe(investment.getExchange()))
                    .append("\",\"status\":\"").append(promptSafe(investment.getStatus()))
                    .append("\",\"purchasePrice\":").append(numberOrNull(investment.getPurchasePrice()))
                    .append(",\"currentPrice\":").append(numberOrNull(investment.getCurrentPrice()))
                    .append(",\"quantity\":").append(numberOrNull(investment.getQuantity()))
                    .append(",\"purchaseDate\":\"").append(promptSafe(investment.getPurchaseDate()))
                    .append("\",\"description\":\"").append(promptSafe(investment.getDescription()))
                    .append("\",\"searchableSectors\":\"").append(promptSafe(String.join(" ",
                            safe(investment.getMarket()), safe(investment.getAssetClass()),
                            safe(investment.getType()), safe(investment.getDescription()))))
                    .append("\"}\n");
        }
        return prompt.toString();
    }

    private AiSearchResponseDTO parseGeminiRanking(String query, List<Investment> investments, String generated) {
        if (generated == null || generated.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(stripCodeFences(generated));
            String overview = root.path("overview").asText("");
            Map<Long, Investment> investmentsById = investments.stream()
                    .filter(investment -> investment.getInvestmentId() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            Investment::getInvestmentId, investment -> investment,
                            (first, second) -> first, LinkedHashMap::new));
            Set<Long> seenIds = new HashSet<>();
            List<AiSearchResultDTO> results = new ArrayList<>();
            JsonNode resultNodes = root.get("results");
            if (resultNodes == null || !resultNodes.isArray()) {
                return null;
            }
            for (JsonNode result : resultNodes) {
                if (!result.has("investmentId") || !result.has("score") || !result.has("reason")
                        || !result.get("score").isNumber()
                        || !Double.isFinite(result.get("score").asDouble())
                        || !result.get("reason").isTextual()) {
                    return null;
                }
                long id = result.get("investmentId").asLong();
                Investment investment = investmentsById.get(id);
                if (investment == null || !seenIds.add(id)) return null;
                List<String> factors = result.path("matchedFactors").isArray()
                        ? objectMapper.convertValue(result.path("matchedFactors"), List.class) : List.of();
                results.add(new AiSearchResultDTO(ApiDtoMapper.toInvestmentResponse(investment), result.get("score").asDouble(),
                        factors, result.get("reason").asText()));
            }
            logger.info("Gemini returned {} ranked results", results.size());
            return new AiSearchResponseDTO(query, overview, true, results);
        } catch (Exception exception) {
            logger.warn("Gemini ranking returned invalid JSON; using fallback ranking");
            return null;
        }
    }

    private List<Investment> fallbackCandidates(String query, List<Investment> investments) {
        try {
            return searchInvestments(query);
        } catch (UnrelatedInvestmentQueryException exception) {
            logger.info("Fallback ranking found no investment matches");
            return List.of();
        }
    }

    private AiSearchResponseDTO fallbackResponse(String query, List<Investment> candidates,
                                              boolean semanticRankingUsed) {
        List<AiSearchResultDTO> results = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Investment investment = candidates.get(i);
            results.add(new AiSearchResultDTO(
                        ApiDtoMapper.toInvestmentResponse(investment),
                        rankScore(i, candidates.size()),
                        matchingFactors(query, investment),
                        "Matched using deterministic investment-field and semantic ranking."
                ));
        }
        return new AiSearchResponseDTO(query,
                candidates.isEmpty() ? "No matching investments were found."
                        : "Ranked investment candidates matching your query. Risk level is not stored for these investments, so no risk classification was inferred.",
                semanticRankingUsed,
                results);
    }

    private double rankScore(int rank, int resultCount) {
        return resultCount <= 1 ? 1.0 : Math.round((1.0 - (rank / (double) resultCount)) * 100.0) / 100.0;
    }

    private String numberOrNull(Number value) {
        return value == null ? "null" : value.toString();
    }

    private List<String> matchingFactors(String query, Investment investment) {
        String normalized = query.toLowerCase(Locale.ROOT);
        List<String> factors = new ArrayList<>();
        if (contains(normalized, investment.getName())) factors.add("name");
        if (contains(normalized, investment.getType())) factors.add("type");
        if (containsAny(tokenize(query), "stock", "equity", "share")
                && containsAny(tokenize(investment.getType()), "stock", "equity", "share")) {
            factors.add("stock / equity intent");
        }
        if (contains(normalized, investment.getMarket())) factors.add("market");
        if (contains(normalized, investment.getAssetClass())) factors.add("asset class");
        if (contains(normalized, investment.getStatus())) factors.add("status");
        return factors;
    }

    private boolean contains(String query, String value) {
        return value != null && !value.isBlank()
                && query.contains(value.toLowerCase(Locale.ROOT));
    }

    private String promptSafe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", " ").replace("\n", " ");
    }

    private String stripCodeFences(String value) {
        return value.replaceFirst("^\\s*```(?:json)?\\s*", "")
                .replaceFirst("\\s*```\\s*$", "").trim();
    }

    private Map<Long, float[]> getEmbeddingsForInvestments(List<Investment> investments) {
            Map<Long, float[]> vectors = new ConcurrentHashMap<>();
            List<Investment> uncachedInvestments = new ArrayList<>();
            List<String> uncachedProfiles = new ArrayList<>();

            for (Investment investment : investments) {
                if (investment == null || investment.getInvestmentId() == null) {
                    continue;
                }
                String profileText = buildProfileText(investment);
                String fingerprint = hashText(profileText);
                CachedEmbedding cached = embeddingCache.get(investment.getInvestmentId());
                if (cached != null && fingerprint.equals(cached.fingerprint())) {
                    vectors.put(investment.getInvestmentId(), cached.vector());
                } else {
                    uncachedInvestments.add(investment);
                    uncachedProfiles.add(profileText);
                }
            }

            if (!uncachedProfiles.isEmpty()) {
                for (int start = 0; start < uncachedProfiles.size(); start += 100) {
                    int end = Math.min(start + 100, uncachedProfiles.size());
                    List<float[]> generated = geminiService.embedBatch(uncachedProfiles.subList(start, end));
                    if (generated.size() == end - start) {
                        for (int i = 0; i < generated.size(); i++) {
                            int index = start + i;
                            Investment investment = uncachedInvestments.get(index);
                            float[] vector = generated.get(i);
                            embeddingCache.put(investment.getInvestmentId(),
                                    new CachedEmbedding(hashText(uncachedProfiles.get(index)), vector));
                            vectors.put(investment.getInvestmentId(), vector);
                        }
                    }
                }
            }
            return vectors;
        }

    private List<Investment> lexicalFallback(List<Investment> investments, String query) {
        Map<Investment, Double> scores = new LinkedHashMap<>();
        for (Investment investment : investments) {
            String profile = buildProfileText(investment);
            double score = lexicalSimilarity(query, profile);
            score += intentMatchBoost(query, investment);
            scores.put(investment, score);
        }

        if (scores.values().stream().allMatch(score -> score <= 0.0)
                && !hasInvestmentQueryHint(query)) {
            throw new UnrelatedInvestmentQueryException(
                    "Please ask about investments, markets, prices, asset types, or investment status.");
        }

        Set<String> queryTokens = tokenize(query);
        boolean hasRiskAssetIntent = queryTokens.contains("risk")
                && containsAny(queryTokens, "stock", "equity", "share", "fund", "mutual", "bond", "debt");
        return scores.entrySet().stream()
                .filter(entry -> !hasRiskAssetIntent || matchesAssetIntent(query, entry.getKey()))
                .sorted(Map.Entry.<Investment, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> safe(entry.getKey().getName()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(entry -> safe(entry.getKey().getSymbol()), String.CASE_INSENSITIVE_ORDER))
                .map(Map.Entry::getKey)
                .toList();
    }

    private boolean hasInvestmentQueryHint(String query) {
        Set<String> queryTokens = tokenize(query);
        return containsAny(queryTokens,
                "investment", "investments", "invest", "stock", "stocks", "equity", "share",
                "shares", "fund", "funds", "mutual", "bond", "bonds", "debt", "market",
                "markets", "price", "prices", "asset", "assets", "status", "portfolio",
                "risk", "risky", "volatile", "volatility", "return", "returns", "growth");
    }

    private boolean matchesAssetIntent(String query, Investment investment) {
        Set<String> queryTokens = tokenize(query);
        Set<String> typeTokens = tokenize(investment.getType());
        return (containsAny(queryTokens, "stock", "equity", "share")
                && containsAny(typeTokens, "stock", "equity", "share"))
                || (containsAny(queryTokens, "fund", "mutual")
                && containsAny(typeTokens, "fund", "mutual"))
                || (containsAny(queryTokens, "bond", "debt")
                && containsAny(typeTokens, "bond", "debt"));
    }

    private Comparator<ScoredInvestment> scoreComparator() {
        return Comparator.comparingDouble(ScoredInvestment::score).reversed()
                .thenComparing(item -> safe(item.investment().getName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> safe(item.investment().getSymbol()), String.CASE_INSENSITIVE_ORDER);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }

        double numerator = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            numerator += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return numerator / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double lexicalSimilarity(String query, String text) {
        if (query == null || text == null) {
            return 0.0;
        }

        Set<String> queryTokens = tokenize(query);
        Set<String> textTokens = tokenize(text);
        if (queryTokens.isEmpty() || textTokens.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(queryTokens);
        intersection.retainAll(textTokens);

        double score = intersection.size() / (double) queryTokens.size();
        String normalizedText = text.toLowerCase(Locale.ROOT);
        for (String token : queryTokens) {
            if (normalizedText.contains(token)) {
                score += 0.05;
            }
        }
        return score;
    }

    private double intentMatchBoost(String query, Investment investment) {
        Set<String> queryTokens = tokenize(query);
        double boost = 0.0;

        if (containsAny(queryTokens, "stock", "equity", "share")
                && containsAny(tokenize(investment.getType()), "stock", "equity", "share")) {
            boost += 0.35;
        }
        if (containsAny(queryTokens, "fund", "mutual")
                && containsAny(tokenize(investment.getType()), "fund", "mutual")) {
            boost += 0.35;
        }
        if (containsAny(queryTokens, "bond", "debt")
                && containsAny(tokenize(investment.getType()), "bond", "debt")) {
            boost += 0.35;
        }
        return boost;
    }

    private boolean containsAny(Set<String> values, String... expected) {
        for (String value : expected) {
            if (values.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> tokenize(String text) {
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        Set<String> tokens = new HashSet<>();
        if (normalized.isEmpty()) {
            return tokens;
        }

        for (String token : normalized.split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(normalizeToken(token));
            }
        }
        return tokens;
    }

    private String normalizeToken(String token) {
        if (token.length() > 4 && token.endsWith("ies")) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.length() > 3 && token.endsWith("s")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private String buildProfileText(Investment investment) {
        if (investment == null) {
            return "";
        }

        return String.join(" ",
                safe(investment.getName()),
                safe(investment.getDescription()),
                safe(investment.getType()),
                safe(investment.getSymbol()),
                safe(investment.getExchange()),
                safe(investment.getMarket()),
                safe(investment.getAssetClass()),
                safe(investment.getCurrency()),
                String.valueOf(investment.getPurchasePrice()),
                String.valueOf(investment.getCurrentPrice()),
                String.valueOf(investment.getQuantity()),
                safe(investment.getPurchaseDate()),
                safe(investment.getStatus())
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String hashText(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : encoded) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return text;
        }
    }

    private record ScoredInvestment(Investment investment, double score) {
    }

    private record CachedEmbedding(String fingerprint, float[] vector) {
    }
}