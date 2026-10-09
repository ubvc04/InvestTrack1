package com.examly.springapp.service;

import com.examly.springapp.dto.AiAnalysisDTO;
import com.examly.springapp.dto.AiMatchCriterionDTO;
import com.examly.springapp.exceptions.UnrelatedInvestmentQueryException;
import com.examly.springapp.model.Investment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic AI investment matching engine.
 *
 * <h2>Workflow</h2>
 * <ol>
 *   <li>Gemini interprets the unmodified natural-language query and extracts weighted
 *       investment criteria as JSON ({@code engine = "gemini-criteria"}). When Gemini is
 *       disabled, fails or returns invalid JSON, a documented deterministic keyword/phrase
 *       extractor is used instead and the response is honestly labelled
 *       {@code engine = "deterministic-fallback"} — Gemini is never credited for work it
 *       did not do.</li>
 *   <li>Every eligible investment loaded from MySQL is compared against each extracted
 *       criterion using stored fields only ({@code type}, {@code market}, {@code assetClass},
 *       {@code description}, {@code status}, {@code currentPrice}, {@code purchasePrice},
 *       {@code purchaseDate}, {@code exchange}).</li>
 *   <li>A weighted score produces a match percentage between 0 and 100.</li>
 *   <li>Results are sorted by match percentage (descending) with a stable secondary sort on
 *       name then symbol, filtered by the configured threshold and returned with per-criterion
 *       explanations.</li>
 * </ol>
 *
 * <h2>Scoring policy (documented, reproducible)</h2>
 * <pre>
 *   score = sum(weight_i * value_i) over verifiable criteria
 *           ------------------------------------------------ x 100        (0..100, integer)
 *           sum(weight_i) over verifiable criteria
 *
 *   value_i = 1.0 for MATCH, 0.0 for MISMATCH
 *   confidence = verifiable weight / requested weight x 100
 * </pre>
 * <ul>
 *   <li>Weights are taken from the criteria extraction (Gemini or fallback defaults) and
 *       normalized so the requested weights total 100.</li>
 *   <li>Only criteria the user actually mentioned are created, so nothing else is penalized.</li>
 *   <li>{@code UNVERIFIABLE} criteria (risk tolerance, investment horizon, liquidity,
 *       volatility, and any criterion whose input cannot be read) are excluded from both the
 *       numerator and the denominator: a missing database field never counts as a mismatch and
 *       never invents a value. The resulting information loss is reported through
 *       {@code confidence} per result and through the analysis panel's unverified list.</li>
 *   <li>If no requested criterion can be verified the score is 0 and the confidence is 0;
 *       the analysis panel explains that the stored data cannot answer the question.</li>
 *   <li>The same query against unchanged data always produces the same percentages.</li>
 * </ul>
 *
 * <p>The percentage measures how closely a record fits the stated criteria. It is not a
 * probability of profit, a risk rating, a recommendation or a prediction of future performance.</p>
 */
@Service
public class InvestmentMatchEngine {

    private static final Logger logger = LoggerFactory.getLogger(InvestmentMatchEngine.class);

    public static final String ENGINE_GEMINI = "gemini-criteria";
    public static final String ENGINE_FALLBACK = "deterministic-fallback";

    public static final String STATE_MATCH = "MATCH";
    public static final String STATE_MISMATCH = "MISMATCH";
    public static final String STATE_UNVERIFIABLE = "UNVERIFIABLE";

    public static final String UNRELATED_MESSAGE =
            "The AI search is designed for investment-related questions. Please ask about asset "
                    + "types, sectors, markets, budgets, risk tolerance, time horizon or investment status.";

    // Criterion keys -----------------------------------------------------------------------
    public static final String K_CATEGORY = "category";
    public static final String K_SECTOR = "sector";
    public static final String K_RISK = "risk";
    public static final String K_HORIZON = "horizon";
    public static final String K_BUDGET = "budget";
    public static final String K_LIQUIDITY = "liquidity";
    public static final String K_INCOME_VS_GROWTH = "incomeVsGrowth";
    public static final String K_RETURN_OBJECTIVE = "returnObjective";
    public static final String K_GEOGRAPHY = "geography";
    public static final String K_STATUS = "status";
    public static final String K_VOLATILITY = "volatility";

    /** Human readable labels used in the UI when the extraction does not supply one. */
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry(K_CATEGORY, "Instrument type"),
            Map.entry(K_SECTOR, "Sector"),
            Map.entry(K_RISK, "Risk tolerance"),
            Map.entry(K_HORIZON, "Investment horizon"),
            Map.entry(K_BUDGET, "Budget"),
            Map.entry(K_LIQUIDITY, "Liquidity"),
            Map.entry(K_INCOME_VS_GROWTH, "Income vs growth"),
            Map.entry(K_RETURN_OBJECTIVE, "Return objective"),
            Map.entry(K_GEOGRAPHY, "Geographic market"),
            Map.entry(K_STATUS, "Investment status"),
            Map.entry(K_VOLATILITY, "Volatility"));

    /** Default relative weights, applied when the extraction does not supply one. */
    private static final Map<String, Double> DEFAULT_WEIGHTS = Map.ofEntries(
            Map.entry(K_CATEGORY, 40d),
            Map.entry(K_SECTOR, 30d),
            Map.entry(K_BUDGET, 30d),
            Map.entry(K_GEOGRAPHY, 25d),
            Map.entry(K_INCOME_VS_GROWTH, 25d),
            Map.entry(K_RETURN_OBJECTIVE, 25d),
            Map.entry(K_STATUS, 20d),
            Map.entry(K_RISK, 25d),
            Map.entry(K_HORIZON, 25d),
            Map.entry(K_LIQUIDITY, 20d),
            Map.entry(K_VOLATILITY, 20d));

    private static final Set<String> KNOWN_KEYS = Set.of(
            K_CATEGORY, K_SECTOR, K_RISK, K_HORIZON, K_BUDGET, K_LIQUIDITY,
            K_INCOME_VS_GROWTH, K_RETURN_OBJECTIVE, K_GEOGRAPHY, K_STATUS, K_VOLATILITY);

    private static final Set<String> STRUCTURALLY_UNVERIFIABLE = Set.of(
            K_RISK, K_HORIZON, K_LIQUIDITY, K_VOLATILITY);

    /** Requested instrument types are mapped onto the canonical values stored in MySQL. */
    private static final Map<String, String> TYPE_ALIASES = new LinkedHashMap<>();

    static {
        putType("stock", "Stock");
        putType("stocks", "Stock");
        putType("equity", "Stock");
        putType("equities", "Stock");
        putType("share", "Stock");
        putType("shares", "Stock");
        putType("bond", "Bond");
        putType("bonds", "Bond");
        putType("debt", "Bond");
        putType("fixed income", "Bond");
        putType("fixed-income", "Bond");
        putType("note", "Bond");
        putType("notes", "Bond");
        putType("treasury", "Bond");
        putType("etf", "ETF");
        putType("exchange traded fund", "ETF");
        putType("exchange-traded fund", "ETF");
        putType("index fund", "ETF");
        putType("mutual fund", "Mutual Fund");
        putType("mutual funds", "Mutual Fund");
        putType("fund", "Mutual Fund");
        putType("funds", "Mutual Fund");
        putType("commodity", "Commodity");
        putType("commodities", "Commodity");
        putType("gold", "Commodity");
        putType("silver", "Commodity");
        putType("crypto", "Cryptocurrency");
        putType("crypto currency", "Cryptocurrency");
        putType("cryptocurrency", "Cryptocurrency");
        putType("cryptocurrencies", "Cryptocurrency");
        putType("bitcoin", "Cryptocurrency");
        putType("digital asset", "Cryptocurrency");
        putType("token", "Cryptocurrency");
        putType("real estate", "Real Estate");
        putType("real-estate", "Real Estate");
        putType("reit", "Real Estate");
        putType("reits", "Real Estate");
        putType("property", "Real Estate");
    }

    private static void putType(String alias, String canonical) {
        TYPE_ALIASES.put(alias, canonical);
    }

    /** Sector vocabulary: canonical sector -> synonyms used for query and record matching. */
    private static final Map<String, List<String>> SECTOR_LEXICON = new LinkedHashMap<>();

    static {
        SECTOR_LEXICON.put("technology", List.of("technolog", "software", "cloud", "semiconductor",
                "internet", "search", "advertising", "electronics", "artificial intelligence", " ai ",
                "data centre", "digital service"));
        SECTOR_LEXICON.put("healthcare", List.of("health", "pharma", "medical", "biotech", "drug",
                "hospital", "clinical", "animal health"));
        SECTOR_LEXICON.put("energy", List.of("energy", "oil", "gas", "petroleum", "solar", "wind power",
                "renewable", "refinery", "pipeline", "drilling"));
        SECTOR_LEXICON.put("utilities", List.of("utilit", "electricity", "water network", "power generation"));
        SECTOR_LEXICON.put("financials", List.of("bank", "financial", "insurance", "investment banking",
                "lending", "credit", "payments", "wealth management", "asset management"));
        SECTOR_LEXICON.put("consumer", List.of("consumer", "retail", "apparel", "beverage", "food",
                "household", "personal care", "grocery", "restaurant"));
        SECTOR_LEXICON.put("industrial", List.of("industrial", "aerospace", "defense", "machinery",
                "manufacturing", "logistics", "warehouse", "jet engine", "rail"));
        SECTOR_LEXICON.put("automotive", List.of("automobile", "automotive", " vehicle", "electric car"));
        SECTOR_LEXICON.put("telecom", List.of("telecom", "wireless", "broadband", "mobile network"));
        SECTOR_LEXICON.put("materials", List.of("material", "mining", "chemical", "steel", "metal",
                "copper", "lumber"));
        SECTOR_LEXICON.put("real estate", List.of("real estate", "real-estate", "reit", "property",
                "warehouse", "logistics space", "residential", "commercial property"));
        SECTOR_LEXICON.put("media", List.of("media", "entertainment", "streaming", "gaming", "publishing"));
        SECTOR_LEXICON.put("crypto", List.of("crypto", "blockchain", "token", "digital asset", "mining reward"));
        SECTOR_LEXICON.put("agriculture", List.of("agricultur", "farm", "crop", "fertilizer"));
        SECTOR_LEXICON.put("precious metals", List.of("gold", "silver", "bullion", "precious metal"));
        SECTOR_LEXICON.put("energy commodities", List.of("crude", "brent", "natural gas"));
    }

    /** Geographic vocabulary: canonical market -> synonyms. */
    private static final Map<String, List<String>> GEOGRAPHY_LEXICON = new LinkedHashMap<>();

    static {
        GEOGRAPHY_LEXICON.put("United States", List.of("us equities", "u.s.", " united states", " america",
                "nasdaq", "nyse", " us ", "usa"));
        GEOGRAPHY_LEXICON.put("India", List.of("india", "indian", "nse", "bse", "sensex"));
        GEOGRAPHY_LEXICON.put("Europe", List.of("europe", "european", "euronext", "frankfurt", "dax"));
        GEOGRAPHY_LEXICON.put("United Kingdom", List.of("united kingdom", "britain", "london", "ftse"));
        GEOGRAPHY_LEXICON.put("Japan", List.of("japan", "nikkei", "tokyo"));
        GEOGRAPHY_LEXICON.put("Asia Pacific", List.of("asia", "asian", "pacific", "hong kong", "china",
                "singapore", "australia"));
        GEOGRAPHY_LEXICON.put("Global", List.of("global", "worldwide", "international", "across markets",
                "emerging market"));
        GEOGRAPHY_LEXICON.put("Emerging Markets", List.of("emerging", "frontier market"));
    }

    private static final List<String> INCOME_WORDING = List.of(
            "dividend", "income", "yield", "coupon", "interest", "distribution", "paid to holders");
    private static final List<String> GROWTH_WORDING = List.of(
            "growth", "appreciation", "capital gain", "expand", "expansion", "accumulate", "wealth creation");

    private static final Pattern BUDGET_PATTERN = Pattern.compile(
            "(?:under|below|less than|upto|up to|maximum|max|budget(?: of)?|within|afford(?:able)?|cheaper than)\\s*"
                    + "(?:[$€£₹]|usd|eur|gbp|inr|rs\\.?|dollars?|rupees?)?\\s*"
                    + "([0-9][0-9,]*(?:\\.[0-9]+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BUDGET_SUFFIX_PATTERN = Pattern.compile(
            "([$€£₹]|usd|eur|gbp|inr|rs\\.?|dollars?|rupees?)?\\s*"
                    + "([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(?:or less|or below|or under|max|maximum)",
            Pattern.CASE_INSENSITIVE);

    private final GeminiService geminiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InvestmentMatchEngine(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    // -------------------------------------------------------------------------------------
    // Public model
    // -------------------------------------------------------------------------------------

    /** One extracted, weighted criterion. */
    public record Criterion(String key, String label, double rawWeight, List<String> values) {
    }

    /** The interpretation of one query. */
    public record MatchPlan(String interpretation, boolean investmentRelated, String source,
                            List<Criterion> criteria) {
    }

    /** The full outcome of scoring one investment. */
    public record Evaluation(List<AiMatchCriterionDTO> details, int matchPercentage, int confidence,
                             List<String> matchedLabels, List<String> mismatchedLabels,
                             List<String> unverifiableLabels, String primaryReason) {
    }

    /** One investment plus its evaluation, used for sorting and rendering. */
    public record Scored(Investment investment, Evaluation evaluation) {
    }

    // -------------------------------------------------------------------------------------
    // Step 1: criteria extraction
    // -------------------------------------------------------------------------------------

    /**
     * Builds the match plan for a raw user query. Gemini is used when it is available;
     * otherwise the documented deterministic extractor runs. Both paths may throw
     * {@link UnrelatedInvestmentQueryException} (HTTP 400) for investment-unrelated requests.
     */
    public MatchPlan buildPlan(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            throw new UnrelatedInvestmentQueryException(
                    "Please enter an investment-related search request.");
        }

        if (geminiService.isEnabled()) {
            try {
                String generated = geminiService.generate(buildCriteriaPrompt(normalized));
                MatchPlan plan = parseGeminiPlan(generated);
                if (plan != null) {
                    if (!plan.investmentRelated()) {
                        throw new UnrelatedInvestmentQueryException(UNRELATED_MESSAGE);
                    }
                    logger.info("Gemini extracted {} investment criteria for query [{}]",
                            plan.criteria().size(), normalized);
                    return plan;
                }
                logger.warn("Gemini criteria extraction returned unusable JSON; "
                        + "using the deterministic fallback engine");
            } catch (UnrelatedInvestmentQueryException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                logger.warn("Gemini criteria extraction failed; using the deterministic fallback engine",
                        exception);
            }
        }

        MatchPlan fallback = fallbackPlan(normalized);
        if (!fallback.investmentRelated()) {
            throw new UnrelatedInvestmentQueryException(UNRELATED_MESSAGE);
        }
        logger.info("Fallback engine extracted {} investment criteria for query [{}]",
                fallback.criteria().size(), normalized);
        return fallback;
    }

    private String buildCriteriaPrompt(String query) {
        return """
                You interpret a natural-language investment search request and extract the criteria
                the user actually stated. Return ONLY valid JSON:
                {"investmentRelated":true,"interpretation":"one sentence describing what the user wants",
                 "criteria":[{"key":"category","label":"Instrument type","weight":40,"values":["Stock"]}]}

                Allowed keys, their labels and whether stored investment data can verify them:
                  category        -> "Instrument type"        (verifiable: type field)
                  sector          -> "Sector"                 (verifiable: market, asset class, description)
                  status          -> "Investment status"      (verifiable: status field, Active/Sold)
                  budget          -> "Budget"                 (verifiable: current price; put a plain number in values)
                  geography       -> "Geographic market"      (verifiable: market, exchange, description)
                  incomeVsGrowth  -> "Income vs growth"       (verifiable: description wording)
                  returnObjective -> "Return objective"       (verifiable from recorded price change)
                  risk            -> "Risk tolerance"         (NOT verifiable: risk is not stored)
                  horizon         -> "Investment horizon"     (NOT verifiable: no horizon or maturity field)
                  liquidity       -> "Liquidity"              (NOT verifiable: not stored)
                  volatility      -> "Volatility"             (NOT verifiable: not stored)

                Rules:
                - weight: relative importance 1-100 for criteria the user actually stated. Never add
                  criteria the user did not mention.
                - values: short requested qualifiers, empty array when the criterion is open-ended.
                - Interpret arbitrary phrasing and synonyms; do not require predefined categories.
                - investmentRelated: false when the request has nothing to do with investments,
                  markets, money, assets, prices, portfolios or finance.
                - Never invent prices, returns, risk ratings or market data.

                USER QUERY:
                """ + promptSafe(query);
    }

    private MatchPlan parseGeminiPlan(String generated) {
        if (generated == null || generated.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(stripCodeFences(generated));
            if (!root.isObject()) {
                return null;
            }
            boolean investmentRelated = !root.has("investmentRelated")
                    || root.get("investmentRelated").asBoolean(true);
            String interpretation = root.path("interpretation").asText("").trim();
            List<Criterion> criteria = new ArrayList<>();
            JsonNode nodes = root.get("criteria");
            if (nodes != null && nodes.isArray()) {
                Set<String> seen = new LinkedHashSet<>();
                for (JsonNode node : nodes) {
                    String key = node.path("key").asText("").trim();
                    if (!KNOWN_KEYS.contains(key) || !seen.add(key)) {
                        continue;
                    }
                    String label = node.path("label").asText("").trim();
                    if (label.isBlank()) {
                        label = LABELS.get(key);
                    }
                    double weight = node.path("weight").isNumber()
                            ? node.path("weight").asDouble() : defaultWeight(key);
                    List<String> values = readValues(node.get("values"));
                    criteria.add(new Criterion(key, limit(label, 60), clampWeight(weight), values));
                }
            }
            if (!investmentRelated) {
                return new MatchPlan(interpretation, false, ENGINE_GEMINI, List.of());
            }
            if (interpretation.isBlank() && criteria.isEmpty()) {
                return null;
            }
            return new MatchPlan(interpretation.isBlank()
                    ? "Investment search based on your request." : limit(interpretation, 300),
                    true, ENGINE_GEMINI, List.copyOf(criteria));
        } catch (Exception exception) {
            logger.warn("Gemini criteria JSON could not be parsed: {}", exception.getMessage());
            return null;
        }
    }

    private List<String> readValues(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode value : node) {
                String text = value.asText("").trim();
                if (!text.isBlank()) {
                    values.add(limit(text, 60));
                }
                if (values.size() >= 6) {
                    break;
                }
            }
        } else if (node != null && node.isTextual() && !node.asText().isBlank()) {
            values.add(limit(node.asText().trim(), 60));
        }
        return values;
    }

    /**
     * Deterministic, documented fallback criteria extractor. Used only when Gemini is
     * unavailable or returned unusable output; the response is labelled
     * {@link #ENGINE_FALLBACK} so the UI never presents it as Gemini-generated.
     */
    private MatchPlan fallbackPlan(String query) {
        String text = " " + query.toLowerCase(Locale.ROOT) + " ";
        List<Criterion> criteria = new ArrayList<>();

        Set<String> types = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : TYPE_ALIASES.entrySet()) {
            if (text.contains(" " + entry.getKey()) || text.contains(entry.getKey() + " ")
                    || text.trim().equals(entry.getKey())) {
                types.add(entry.getValue());
            }
        }
        if (!types.isEmpty()) {
            criteria.add(new Criterion(K_CATEGORY, LABELS.get(K_CATEGORY), DEFAULT_WEIGHTS.get(K_CATEGORY),
                    new ArrayList<>(types)));
        }

        String sector = detectLexicon(text, SECTOR_LEXICON);
        if (sector != null) {
            criteria.add(new Criterion(K_SECTOR, LABELS.get(K_SECTOR), DEFAULT_WEIGHTS.get(K_SECTOR),
                    List.of(sector)));
        }

        String risk = containsAnyWord(text, "low risk", "low-risk", "conservative", "safe", "secure", "capital preservation")
                ? "low"
                : containsAnyWord(text, "high risk", "high-risk", "aggressive", "risky", "volatile", "speculative")
                ? "high"
                : containsAnyWord(text, "moderate risk", "moderate-risk", "balanced", "medium risk", "moderate")
                ? "moderate" : null;
        if (risk != null) {
            criteria.add(new Criterion(K_RISK, LABELS.get(K_RISK), DEFAULT_WEIGHTS.get(K_RISK), List.of(risk)));
        }

        String horizon = containsAnyWord(text, "long-term", "long term", "longer term", "over the long run",
                "retirement", "10 years", "20 years", "5 years", "multi year", "years to grow")
                ? "long-term"
                : containsAnyWord(text, "short-term", "short term", "quick return", "near term", "immediate",
                "few months", "this year") ? "short-term" : null;
        if (horizon != null) {
            criteria.add(new Criterion(K_HORIZON, LABELS.get(K_HORIZON), DEFAULT_WEIGHTS.get(K_HORIZON),
                    List.of(horizon)));
        }

        String budget = extractBudget(text);
        if (budget != null) {
            criteria.add(new Criterion(K_BUDGET, LABELS.get(K_BUDGET), DEFAULT_WEIGHTS.get(K_BUDGET),
                    List.of(budget)));
        }

        if (containsAnyWord(text, "dividend", "passive income", "income stream", "yield", "regular income")) {
            criteria.add(new Criterion(K_INCOME_VS_GROWTH, LABELS.get(K_INCOME_VS_GROWTH),
                    DEFAULT_WEIGHTS.get(K_INCOME_VS_GROWTH), List.of("income")));
        } else if (containsAnyWord(text, "growth", "appreciation", "capital gain", "wealth creation",
                "increase in value", "accumulate")) {
            criteria.add(new Criterion(K_INCOME_VS_GROWTH, LABELS.get(K_INCOME_VS_GROWTH),
                    DEFAULT_WEIGHTS.get(K_INCOME_VS_GROWTH), List.of("growth")));
        }

        String returnObjective = containsAnyWord(text, "high return", "high returns", "strong return",
                "strong returns", "best performing", "top performing", "outperform", "high growth")
                ? "high"
                : containsAnyWord(text, "steady return", "stable return", "consistent return",
                "conservative return") ? "stable" : null;
        if (returnObjective != null) {
            criteria.add(new Criterion(K_RETURN_OBJECTIVE, LABELS.get(K_RETURN_OBJECTIVE),
                    DEFAULT_WEIGHTS.get(K_RETURN_OBJECTIVE), List.of(returnObjective)));
        }

        String geography = detectLexicon(text, GEOGRAPHY_LEXICON);
        if (geography != null) {
            criteria.add(new Criterion(K_GEOGRAPHY, LABELS.get(K_GEOGRAPHY),
                    DEFAULT_WEIGHTS.get(K_GEOGRAPHY), List.of(geography)));
        }

        if (containsAnyWord(text, "active", "available", "currently held", "open for")) {
            criteria.add(new Criterion(K_STATUS, LABELS.get(K_STATUS), DEFAULT_WEIGHTS.get(K_STATUS),
                    List.of("Active")));
        } else if (containsAnyWord(text, "sold", "already sold", "disposed", "exited")) {
            criteria.add(new Criterion(K_STATUS, LABELS.get(K_STATUS), DEFAULT_WEIGHTS.get(K_STATUS),
                    List.of("Sold")));
        }

        if (containsAnyWord(text, "liquid", "liquidity", "quickly cash")) {
            criteria.add(new Criterion(K_LIQUIDITY, LABELS.get(K_LIQUIDITY), DEFAULT_WEIGHTS.get(K_LIQUIDITY),
                    List.of("liquid")));
        }
        if (containsAnyWord(text, "stable value", "low volatility", "less volatile", "steady value")) {
            criteria.add(new Criterion(K_VOLATILITY, LABELS.get(K_VOLATILITY),
                    DEFAULT_WEIGHTS.get(K_VOLATILITY), List.of("low")));
        }

        boolean investmentRelated = hasInvestmentQueryHint(query) || !criteria.isEmpty();
        String interpretation = buildFallbackInterpretation(criteria);
        return new MatchPlan(interpretation, investmentRelated, ENGINE_FALLBACK, List.copyOf(criteria));
    }

    private String buildFallbackInterpretation(List<Criterion> criteria) {
        if (criteria.isEmpty()) {
            return "Investment search based on your request.";
        }
        List<String> parts = new ArrayList<>();
        for (Criterion criterion : criteria) {
            if (criterion.values().isEmpty()) {
                parts.add(criterion.label().toLowerCase(Locale.ROOT));
            } else {
                parts.add(criterion.label().toLowerCase(Locale.ROOT) + ": "
                        + String.join(", ", criterion.values()).toLowerCase(Locale.ROOT));
            }
        }
        return limit("You are looking for " + String.join("; ", parts) + ".", 300);
    }

    // -------------------------------------------------------------------------------------
    // Step 2: evaluation of every eligible investment
    // -------------------------------------------------------------------------------------

    /** Scores every investment against the plan and returns them sorted by match percentage. */
    public List<Scored> rank(MatchPlan plan, List<Investment> investments) {
        List<Criterion> criteria = normalizeWeights(plan.criteria());
        List<Scored> scored = new ArrayList<>(investments.size());
        for (Investment investment : investments) {
            scored.add(new Scored(investment, evaluate(criteria, investment)));
        }
        // Descending match percentage, then descending confidence, then a stable name/symbol
        // tiebreak. Each .reversed() is scoped to its own key (reversing a composed chain
        // would invert the whole ordering).
        scored.sort(Comparator
                .comparingInt((Scored item) -> item.evaluation().matchPercentage()).reversed()
                .thenComparing(Comparator
                        .comparingInt((Scored item) -> item.evaluation().confidence()).reversed())
                .thenComparing(item -> safe(item.investment().getName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> safe(item.investment().getSymbol()), String.CASE_INSENSITIVE_ORDER));
        return scored;
    }

    /** Scales the requested weights so that their total is exactly 100. */
    public List<Criterion> normalizeWeights(List<Criterion> criteria) {
        if (criteria.isEmpty()) {
            return List.of();
        }
        double total = 0;
        for (Criterion criterion : criteria) {
            total += criterion.rawWeight();
        }
        if (total <= 0) {
            total = criteria.size();
        }
        List<Criterion> normalized = new ArrayList<>(criteria.size());
        for (Criterion criterion : criteria) {
            double raw = criterion.rawWeight() > 0 ? criterion.rawWeight() : 1;
            double weight = Math.round((raw / total) * 1000.0) / 10.0;
            normalized.add(new Criterion(criterion.key(), criterion.label(), weight, criterion.values()));
        }
        return normalized;
    }

    private Evaluation evaluate(List<Criterion> criteria, Investment investment) {
        List<AiMatchCriterionDTO> details = new ArrayList<>();
        List<String> matched = new ArrayList<>();
        List<String> mismatched = new ArrayList<>();
        List<String> unverifiable = new ArrayList<>();

        double requestedWeight = 0;
        double verifiableWeight = 0;
        double earnedWeight = 0;

        for (Criterion criterion : criteria) {
            CriterionOutcome outcome = evaluateCriterion(criterion, investment);
            requestedWeight += criterion.rawWeight();
            if (!STATE_UNVERIFIABLE.equals(outcome.state())) {
                verifiableWeight += criterion.rawWeight();
                if (STATE_MATCH.equals(outcome.state())) {
                    earnedWeight += criterion.rawWeight();
                }
            }
            details.add(new AiMatchCriterionDTO(criterion.label(), outcome.state(),
                    criterion.rawWeight(), outcome.evidence()));
            if (STATE_MATCH.equals(outcome.state())) {
                matched.add(criterion.label());
            } else if (STATE_MISMATCH.equals(outcome.state())) {
                mismatched.add(criterion.label());
            } else {
                unverifiable.add(criterion.label());
            }
        }

        int score = verifiableWeight <= 0 ? 0
                : (int) Math.round((earnedWeight / verifiableWeight) * 100.0);
        int confidence = requestedWeight <= 0 ? 0
                : (int) Math.round((verifiableWeight / requestedWeight) * 100.0);

        return new Evaluation(details, clampPercent(score), clampPercent(confidence),
                List.copyOf(matched), List.copyOf(mismatched), List.copyOf(unverifiable),
                primaryReason(details, investment));
    }

    private record CriterionOutcome(String state, String evidence) {
    }

    private CriterionOutcome evaluateCriterion(Criterion criterion, Investment investment) {
        return switch (criterion.key()) {
            case K_CATEGORY -> evaluateCategory(criterion, investment);
            case K_SECTOR -> evaluateLexical(criterion, investment, SECTOR_LEXICON,
                    buildEvidenceText(investment, investment.getMarket(), investment.getAssetClass(),
                            focusClause(investment.getDescription()), investment.getName()),
                    "sector wording");
            case K_GEOGRAPHY -> evaluateLexical(criterion, investment, GEOGRAPHY_LEXICON,
                    buildEvidenceText(investment, investment.getMarket(), investment.getExchange(),
                            focusClause(investment.getDescription())),
                    "market or region wording");
            case K_STATUS -> evaluateStatus(criterion, investment);
            case K_BUDGET -> evaluateBudget(criterion, investment);
            case K_INCOME_VS_GROWTH -> evaluateIncomeVsGrowth(criterion, investment);
            case K_RETURN_OBJECTIVE -> evaluateReturnObjective(criterion, investment);
            case K_RISK -> new CriterionOutcome(STATE_UNVERIFIABLE,
                    "Risk tolerance is not stored on investment records, so it was not scored.");
            case K_HORIZON -> new CriterionOutcome(STATE_UNVERIFIABLE,
                    "Investment horizon or maturity is not stored, so it was not scored.");
            case K_LIQUIDITY -> new CriterionOutcome(STATE_UNVERIFIABLE,
                    "Liquidity is not stored, so it was not scored.");
            case K_VOLATILITY -> new CriterionOutcome(STATE_UNVERIFIABLE,
                    "Volatility history is not stored, so it was not scored.");
            default -> new CriterionOutcome(STATE_UNVERIFIABLE,
                    "No stored field can verify this criterion.");
        };
    }

    private CriterionOutcome evaluateCategory(Criterion criterion, Investment investment) {
        String actual = safe(investment.getType());
        if (criterion.values().isEmpty()) {
            return new CriterionOutcome(STATE_UNVERIFIABLE,
                    "No instrument type was specified in the query.");
        }
        for (String requested : criterion.values()) {
            if (typeMatches(requested, actual)) {
                return new CriterionOutcome(STATE_MATCH,
                        "Record type '" + actual + "' matches the requested "
                                + String.join(" or ", criterion.values()) + " category.");
            }
        }
        return new CriterionOutcome(STATE_MISMATCH,
                "Record type '" + actual + "' does not match the requested "
                        + String.join(" or ", criterion.values()) + " category.");
    }

    private boolean typeMatches(String requested, String actual) {
        if (actual == null || actual.isBlank() || requested == null || requested.isBlank()) {
            return false;
        }
        String wanted = requested.toLowerCase(Locale.ROOT).trim();
        if (wanted.equals(actual.toLowerCase(Locale.ROOT))) {
            return true;
        }
        String direct = TYPE_ALIASES.get(wanted);
        if (direct != null && direct.equalsIgnoreCase(actual)) {
            return true;
        }
        // Phrases such as "growth stocks" or "technology stocks" still name an instrument type.
        for (Map.Entry<String, String> entry : TYPE_ALIASES.entrySet()) {
            if (wanted.contains(entry.getKey()) && entry.getValue().equalsIgnoreCase(actual)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lexical evidence criteria (sector, geography). A record matches when the requested
     * wording is present, mismatches when the record clearly names different wording, and is
     * unverifiable when the record carries no relevant wording at all.
     */
    private CriterionOutcome evaluateLexical(Criterion criterion, Investment investment,
                                             Map<String, List<String>> lexicon, String recordText,
                                             String evidenceName) {
        if (criterion.values().isEmpty()) {
            return new CriterionOutcome(STATE_UNVERIFIABLE,
                    "No " + evidenceName + " was specified in the query.");
        }
        String text = " " + recordText.toLowerCase(Locale.ROOT) + " ";

        List<String> requestedSynonyms = new ArrayList<>();
        List<String> canonicalRequested = new ArrayList<>();
        for (String value : criterion.values()) {
            String canonical = canonicalLexiconKey(value, lexicon);
            if (canonical != null) {
                canonicalRequested.add(canonical);
                requestedSynonyms.addAll(lexicon.get(canonical));
            } else {
                requestedSynonyms.add(value.toLowerCase(Locale.ROOT));
            }
        }

        for (String synonym : requestedSynonyms) {
            if (text.contains(synonym.toLowerCase(Locale.ROOT))) {
                return new CriterionOutcome(STATE_MATCH,
                        "The record carries " + evidenceName + " for '"
                                + String.join(", ", criterion.values()) + "' (matched on '"
                                + synonym.trim() + "').");
            }
        }

        boolean recordCarriesAnyEvidence = false;
        String firstOtherEvidence = null;
        for (Map.Entry<String, List<String>> entry : lexicon.entrySet()) {
            if (canonicalRequested.contains(entry.getKey())) {
                continue;
            }
            for (String synonym : entry.getValue()) {
                if (text.contains(synonym.toLowerCase(Locale.ROOT))) {
                    recordCarriesAnyEvidence = true;
                    if (firstOtherEvidence == null) {
                        firstOtherEvidence = entry.getKey();
                    }
                    break;
                }
            }
        }
        if (recordCarriesAnyEvidence) {
            return new CriterionOutcome(STATE_MISMATCH,
                    "The record carries " + evidenceName + " for '" + firstOtherEvidence
                            + "', which is not the requested '"
                            + String.join(", ", criterion.values()) + "'.");
        }
        return new CriterionOutcome(STATE_UNVERIFIABLE,
                "The record does not state any " + evidenceName + ", so '"
                        + String.join(", ", criterion.values()) + "' could not be verified.");
    }

    private CriterionOutcome evaluateStatus(Criterion criterion, Investment investment) {
        if (criterion.values().isEmpty()) {
            return new CriterionOutcome(STATE_UNVERIFIABLE, "No status was specified in the query.");
        }
        String actual = safe(investment.getStatus());
        for (String requested : criterion.values()) {
            if (requested != null && requested.trim().equalsIgnoreCase(actual)) {
                return new CriterionOutcome(STATE_MATCH,
                        "Record status '" + actual + "' matches the requested status '"
                                + requested.trim() + "'.");
            }
        }
        return new CriterionOutcome(STATE_MISMATCH,
                "Record status '" + actual + "' does not match the requested status "
                        + String.join(" or ", criterion.values()) + ".");
    }

    private CriterionOutcome evaluateBudget(Criterion criterion, Investment investment) {
        Double limit = firstNumber(criterion.values());
        if (limit == null) {
            return new CriterionOutcome(STATE_UNVERIFIABLE,
                    "No numeric budget could be read from the query.");
        }
        Double price = investment.getCurrentPrice();
        if (price == null) {
            return new CriterionOutcome(STATE_UNVERIFIABLE,
                    "The record has no current price, so the budget cannot be checked.");
        }
        String evidence = "Current price " + formatMoney(price, investment.getCurrency())
                + " per unit versus the requested limit of "
                + formatMoney(limit, investment.getCurrency()) + ".";
        return price <= limit
                ? new CriterionOutcome(STATE_MATCH, evidence)
                : new CriterionOutcome(STATE_MISMATCH, evidence);
    }

    private CriterionOutcome evaluateIncomeVsGrowth(Criterion criterion, Investment investment) {
        if (criterion.values().isEmpty()) {
            return new CriterionOutcome(STATE_UNVERIFIABLE,
                    "No income or growth preference was specified in the query.");
        }
        String preference = criterion.values().get(0).toLowerCase(Locale.ROOT);
        String text = (" " + buildEvidenceText(investment, investment.getName(),
                focusClause(investment.getDescription()), investment.getType(),
                investment.getAssetClass()) + " ")
                .toLowerCase(Locale.ROOT);

        boolean incomeWording = containsAnyWord(text, INCOME_WORDING.toArray(new String[0]));
        boolean growthWording = containsAnyWord(text, GROWTH_WORDING.toArray(new String[0]));

        boolean wantsIncome = preference.contains("income") || preference.contains("dividend")
                || preference.contains("yield");
        if (wantsIncome) {
            if (incomeWording) {
                return new CriterionOutcome(STATE_MATCH,
                        "The record description includes income wording ('"
                                + firstPresent(text, INCOME_WORDING) + "').");
            }
            if (growthWording) {
                return new CriterionOutcome(STATE_MISMATCH,
                        "The record is described with growth wording ('"
                                + firstPresent(text, GROWTH_WORDING) + "') and shows no income wording.");
            }
            return new CriterionOutcome(STATE_UNVERIFIABLE,
                    "The record states no income or distribution wording, so the income preference "
                            + "could not be verified.");
        }

        if (growthWording) {
            return new CriterionOutcome(STATE_MATCH,
                    "The record description includes growth wording ('"
                            + firstPresent(text, GROWTH_WORDING) + "').");
        }
        if (incomeWording) {
            return new CriterionOutcome(STATE_MISMATCH,
                    "The record is described with income wording ('"
                            + firstPresent(text, INCOME_WORDING) + "') and shows no growth wording.");
        }
        return new CriterionOutcome(STATE_UNVERIFIABLE,
                "The record states no growth or income wording, so the growth preference "
                        + "could not be verified.");
    }

    private CriterionOutcome evaluateReturnObjective(Criterion criterion, Investment investment) {
        if (criterion.values().isEmpty()) {
            return new CriterionOutcome(STATE_UNVERIFIABLE,
                    "No return objective was specified in the query.");
        }
        String wanted = criterion.values().get(0).toLowerCase(Locale.ROOT);
        boolean wantsHigh = wanted.contains("high") || wanted.contains("strong")
                || wanted.contains("best") || wanted.contains("outperform");
        if (!wantsHigh) {
            return new CriterionOutcome(STATE_UNVERIFIABLE,
                    "Only recorded price points are stored; steady or low-return behaviour cannot "
                            + "be verified from two price points.");
        }
        Double purchase = investment.getPurchasePrice();
        Double current = investment.getCurrentPrice();
        if (purchase == null || current == null || purchase <= 0) {
            return new CriterionOutcome(STATE_UNVERIFIABLE,
                    "The record lacks comparable price points, so the return objective "
                            + "could not be checked.");
        }
        double change = ((current - purchase) / purchase) * 100.0;
        String evidence = String.format(Locale.ROOT,
                "Recorded price change since purchase is %+.1f%% (historical record, not a forecast).",
                change);
        return change >= 20.0
                ? new CriterionOutcome(STATE_MATCH, evidence)
                : new CriterionOutcome(STATE_MISMATCH, evidence);
    }

    // -------------------------------------------------------------------------------------
    // Step 3: response assembly
    // -------------------------------------------------------------------------------------

    /** Builds the overview text actually shown above the results. */
    public String buildOverview(MatchPlan plan, int evaluated, int matched, int threshold,
                                int highestScore) {
        String engineNote = ENGINE_FALLBACK.equals(plan.source())
                ? " Gemini was unavailable, so the documented deterministic fallback engine was used."
                : " Criteria were interpreted by Gemini and scored deterministically in the backend.";
        if (evaluated == 0) {
            return "The investment database contains no records to evaluate." + engineNote;
        }
        if (plan.criteria().isEmpty()) {
            return "Your query did not state any criterion that can be matched against the stored "
                    + "investment fields, so nothing was ranked. " + engineNote;
        }
        if (matched == 0) {
            return "No investment reached the " + threshold + "% match threshold out of " + evaluated
                    + " evaluated records (highest score " + highestScore + "%). " + engineNote;
        }
        return "Matched " + matched + " of " + evaluated + " evaluated investments against "
                + plan.criteria().size() + " requested criteria. Scores are deterministic match "
                + "percentages calculated from stored investment fields — they are not predictions, "
                + "risk ratings or advice." + engineNote;
    }

    /**
     * Builds the analysis / suggestion panel content from the real evaluation outcome.
     *
     * @param ranked   every evaluated investment, sorted (used for the closest-candidate hint)
     * @param passing  the results that reached the threshold (used for best-match summaries)
     */
    public AiAnalysisDTO buildAnalysis(MatchPlan plan, List<Scored> ranked, List<Scored> passing,
                                       int threshold, int evaluated) {
        int matched = passing.size();
        List<Criterion> normalized = normalizeWeights(plan.criteria());
        List<String> requested = new ArrayList<>();
        for (Criterion criterion : normalized) {
            requested.add(criterion.label());
        }

        List<String> unverified = new ArrayList<>();
        for (Criterion criterion : normalized) {
            if (STRUCTURALLY_UNVERIFIABLE.contains(criterion.key())) {
                unverified.add(criterion.label());
                continue;
            }
            boolean allUnverifiable = true;
            for (Scored item : ranked) {
                for (AiMatchCriterionDTO detail : item.evaluation().details()) {
                    if (detail.criterion().equals(criterion.label())
                            && !STATE_UNVERIFIABLE.equals(detail.state())) {
                        allUnverifiable = false;
                        break;
                    }
                }
                if (!allUnverifiable) {
                    break;
                }
            }
            if (allUnverifiable && !ranked.isEmpty()) {
                unverified.add(criterion.label());
            }
        }

        String objective = plan.interpretation() == null || plan.interpretation().isBlank()
                ? "Investment search based on your request." : plan.interpretation();

        String bestMatches;
        String whyTheyMatch;
        if (matched == 0) {
            int highest = ranked.isEmpty() ? 0 : ranked.get(0).evaluation().matchPercentage();
            bestMatches = "No investment reached the " + threshold + "% threshold"
                    + (ranked.isEmpty() ? "."
                    : "; the closest candidate scored " + highest + "%.");
            whyTheyMatch = ranked.isEmpty()
                    ? "Nothing could be scored because the database contains no investments."
                    : "Closest candidate: " + safe(ranked.get(0).investment().getName()) + " ("
                    + highest + "%). "
                    + sentenceJoin(ranked.get(0).evaluation().details(), STATE_MISMATCH);
        } else {
            StringBuilder builder = new StringBuilder();
            int limit = Math.min(3, matched);
            for (int i = 0; i < limit; i++) {
                Scored item = passing.get(i);
                if (i > 0) {
                    builder.append("; ");
                }
                builder.append(safe(item.investment().getName())).append(" — ")
                        .append(item.evaluation().matchPercentage()).append("% match");
            }
            builder.append(".");
            bestMatches = builder.toString();
            whyTheyMatch = sentenceJoin(passing.get(0).evaluation().details(), STATE_MATCH);
        }

        StringBuilder considerations = new StringBuilder();
        considerations.append("Match percentages describe how closely a record fits your stated ")
                .append("criteria using stored fields only; they are not a probability of profit, ")
                .append("a risk rating or a recommendation.");
        if (!unverified.isEmpty()) {
            considerations.append(" Not verifiable from the database: ")
                    .append(String.join(", ", unverified))
                    .append(" — these were excluded from the score and lowered each result's confidence.");
        }
        if (plan.criteria().isEmpty()) {
            considerations.append(" The query did not state any matchable criterion.");
        }
        considerations.append(" ").append(evaluated)
                .append(" eligible investment records were evaluated for this query.");

        String refine;
        if (plan.criteria().isEmpty()) {
            refine = "Add at least one concrete criterion, for example "
                    + "\"technology stocks under $500 with Active status\".";
        } else if (unverified.size() == normalized.size() && !normalized.isEmpty()) {
            refine = "None of the stated criteria can be verified from the stored investment fields. "
                    + "Rephrase using verifiable criteria such as instrument type, sector, budget, "
                    + "geographic market or investment status.";
        } else if (matched == 0) {
            refine = "Broaden or rephrase the request — drop one criterion or widen a limit, then "
                    + "search again; scores are recalculated for every new query.";
        } else {
            refine = "Compare the leading candidates on price, quantity and status, then narrow the "
                    + "query with a sector or a budget to tighten the ranking.";
        }

        return new AiAnalysisDTO(objective, bestMatches, whyTheyMatch,
                considerations.toString(), refine, List.copyOf(requested), List.copyOf(unverified));
    }

    private String sentenceJoin(List<AiMatchCriterionDTO> details, String state) {
        StringBuilder builder = new StringBuilder();
        int used = 0;
        for (AiMatchCriterionDTO detail : details) {
            if (!state.equals(detail.state())) {
                continue;
            }
            if (used > 0) {
                builder.append(' ');
            }
            builder.append(detail.criterion()).append(": ").append(detail.evidence());
            if (++used >= 3) {
                break;
            }
        }
        if (used == 0) {
            return state.equals(STATE_MATCH)
                    ? "No criterion could be confirmed from stored data."
                    : "No stored field contradicts the request.";
        }
        return builder.toString();
    }

    /** Builds the concise, investment-specific explanation shown on each card. */
    public String buildExplanation(Evaluation evaluation) {
        StringBuilder builder = new StringBuilder();
        builder.append("Match ").append(evaluation.matchPercentage())
                .append("% (confidence ").append(evaluation.confidence()).append("%).");
        int used = 0;
        for (AiMatchCriterionDTO detail : evaluation.details()) {
            if (!STATE_MATCH.equals(detail.state())) {
                continue;
            }
            builder.append(' ').append(detail.criterion()).append(": ").append(detail.evidence());
            if (++used >= 2) {
                break;
            }
        }
        for (AiMatchCriterionDTO detail : evaluation.details()) {
            if (STATE_MISMATCH.equals(detail.state())) {
                builder.append(' ').append(detail.criterion()).append(": ").append(detail.evidence());
                break;
            }
        }
        if (!evaluation.unverifiableLabels().isEmpty()) {
            builder.append(" Not verified: ")
                    .append(String.join(", ", evaluation.unverifiableLabels())).append('.');
        }
        return builder.toString().trim();
    }

    private String primaryReason(List<AiMatchCriterionDTO> details, Investment investment) {
        for (AiMatchCriterionDTO detail : details) {
            if (STATE_MATCH.equals(detail.state())) {
                return detail.evidence();
            }
        }
        if (!details.isEmpty()) {
            return details.get(0).evidence();
        }
        return "No criteria were supplied for this query.";
    }

    // -------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------

    private boolean hasInvestmentQueryHint(String query) {
        String text = " " + query.toLowerCase(Locale.ROOT) + " ";
        return containsAnyWord(text, "investment", "investments", "invest", "stock", "stocks",
                "equity", "share", "shares", "fund", "funds", "mutual", "bond", "bonds", "debt",
                "market", "markets", "price", "prices", "asset", "assets", "status", "portfolio",
                "risk", "risky", "volatile", "volatility", "return", "returns", "growth", "etf",
                "crypto", "commodity", "commodities", "real estate", "reit", "dividend", "budget");
    }

    private boolean containsAnyWord(String text, String... expected) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String word : expected) {
            if (lower.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String firstPresent(String text, List<String> candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    private String detectLexicon(String queryText, Map<String, List<String>> lexicon) {
        String canonical = canonicalLexiconKey(queryText, lexicon);
        if (canonical != null) {
            return canonical;
        }
        String text = " " + queryText + " ";
        for (Map.Entry<String, List<String>> entry : lexicon.entrySet()) {
            for (String synonym : entry.getValue()) {
                if (text.contains(synonym.toLowerCase(Locale.ROOT))) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private String canonicalLexiconKey(String value, Map<String, List<String>> lexicon) {
        if (value == null) {
            return null;
        }
        String wanted = value.toLowerCase(Locale.ROOT).trim();
        if (lexicon.containsKey(wanted)) {
            for (String key : lexicon.keySet()) {
                if (key.equalsIgnoreCase(wanted)) {
                    return key;
                }
            }
        }
        for (Map.Entry<String, List<String>> entry : lexicon.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).contains(wanted) && wanted.length() >= 3) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String extractBudget(String text) {
        Matcher matcher = BUDGET_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = BUDGET_SUFFIX_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return null;
    }

    private Double firstNumber(List<String> values) {
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String digits = value.replaceAll("[^0-9.]", "");
            if (digits.isBlank()) {
                continue;
            }
            try {
                return Double.parseDouble(digits);
            } catch (NumberFormatException ignored) {
                // try the next value
            }
        }
        return null;
    }

    private String buildEvidenceText(Investment investment, String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                builder.append(' ').append(part);
            }
        }
        return builder.toString().trim();
    }

    /**
     * Returns the substantive description of a record: the clause after the first em dash up to
     * the first sentence end, with demo/test prose removed.
     *
     * <p>Catalogue and seed records append UI test wording such as
     * <em>"Included to give semantic search genuine sector vocabulary to rank against"</em> or
     * <em>"so list, filter and search views all return data"</em>. That wording describes the
     * interface, not the instrument, so it must never count as sector, region or income
     * evidence.</p>
     */
    private String focusClause(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String cleaned = stripTestProse(description);
        int dash = cleaned.indexOf('—');
        if (dash >= 0 && dash + 1 < cleaned.length()) {
            String rest = cleaned.substring(dash + 1).trim();
            int end = rest.indexOf('.');
            String clause = (end > 0 ? rest.substring(0, end) : rest).trim();
            if (clause.length() >= 20) {
                return clause;
            }
        }
        return cleaned;
    }

    /** Removes the known interface/demo test sentences from a record description. */
    private String stripTestProse(String description) {
        return description
                .replaceAll("(?i)\\s*Included to\\s+[^.]*\\.", " ")
                .replaceAll("(?i)\\s*Seeded so\\s+[^.]*\\.", " ")
                .replaceAll("(?i)\\s*The row exercises\\s+[^.]*\\.", " ")
                .replaceAll("(?i)\\s*Catalogue record for search and filter testing:\\s*", " ")
                .replaceAll("(?i),?\\s*so list, filter and search views all return data\\.?", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String formatMoney(Double value, String currency) {
        String symbol = "USD".equalsIgnoreCase(currency) || currency == null || currency.isBlank()
                ? "$" : currency + " ";
        return String.format(Locale.ROOT, "%s%,.2f", symbol, value);
    }

    private double defaultWeight(String key) {
        Double weight = DEFAULT_WEIGHTS.get(key);
        return weight == null ? 20d : weight;
    }

    private double clampWeight(double weight) {
        if (!Double.isFinite(weight) || weight <= 0) {
            return 20d;
        }
        return Math.min(weight, 100d);
    }

    private int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String promptSafe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", " ").replace("\n", " ");
    }

    private String stripCodeFences(String value) {
        return value.replaceFirst("^\\s*```(?:json)?\\s*", "")
                .replaceFirst("\\s*```\\s*$", "").trim();
    }
}
