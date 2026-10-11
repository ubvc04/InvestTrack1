package com.examly.springapp.service;

import com.examly.springapp.dto.AiAnalysisDTO;
import com.examly.springapp.dto.AiCriterionStatusDTO;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic AI investment matching engine.
 *
 * <h2>Workflow</h2>
 * <ol>
 *   <li>Groq interprets the unmodified natural-language query and extracts weighted
 *       investment criteria as JSON ({@code engine = "groq-criteria"}). When Groq is
 *       disabled, fails or returns invalid JSON, a documented deterministic keyword/phrase
 *       extractor is used instead and the response is honestly labelled
 *       {@code engine = "deterministic-fallback"} — Groq is never credited for work it
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
 *   score = sum(weight_i * credit_i) over ALL requested criteria
 *           ----------------------------------------------------- x 100        (0..100, integer)
 *           sum(weight_i) over ALL requested criteria
 *
 *   credit_i = 1.0 for MATCH, 0.0 for MISMATCH, 0.0 for UNVERIFIABLE,
 *              and a transparent 0..1 factor for PARTIAL (see historical performance below)
 *
 *   dataCoverage = sum(weight_i where state != UNVERIFIABLE)
 *                  ------------------------------------------ x 100
 *                  sum(weight_i) over ALL requested criteria
 * </pre>
 * <ul>
 *   <li>Weights are taken from the criteria extraction (Groq or fallback defaults) and
 *       normalized so the requested weights total 100.</li>
 *   <li>Only criteria the user actually mentioned are created, so nothing else is penalized.</li>
 *   <li><b>Unverifiable criteria stay in the denominator.</b> A missing database field never
 *       counts as a match and never counts as a mismatch — but it does keep the record from
 *       reaching 100%, because the score only counts criteria the backend actually confirmed.
 *       This is what stops a "high-risk stocks" query from handing every stock a 100% match
 *       while risk is unverifiable.</li>
 *   <li><b>Historical price performance</b> is added by the backend (not the provider) whenever
 *       a return objective is stated. It is graded from the recorded purchase-to-current price
 *       change with documented partial credit — full credit at +100% or better, zero credit at
 *       -20% or worse, linear in between — so every record's score reflects its own stored
 *       data instead of tying with every other record. It is always labelled as history and
 *       never presented as evidence of future returns; the forward-looking return objective
 *       itself stays UNVERIFIABLE.</li>
 *   <li>{@code dataCoverage} reports how much of the request could be evaluated at all. It
 *       replaces the old "confidence" percentage, which was easy to misread as a statistical
 *       confidence.</li>
 *   <li>{@code matchTier} is {@code FULL_MATCH} only when every requested criterion was
 *       verified and satisfied; anything else is {@code PARTIAL_MATCH}.</li>
 *   <li>Criteria are either {@code MANDATORY} hard filters (instrument type, sector, budget,
 *       geographic market, investment status) that can exclude a record, or {@code PREFERENCE}
 *       criteria (risk, horizon, liquidity, volatility, income-vs-growth, return objective,
 *       historical price performance) that only influence ranking.</li>
 *   <li>The same query against unchanged data always produces the same percentages. Results are
 *       sorted by confirmed-fit score descending, then data coverage descending, then name,
 *       symbol and record id — a stable, fully deterministic order.</li>
 * </ul>
 *
 * <p>The percentage measures how closely a record fits the stated criteria. It is not a
 * probability of profit, a risk rating, a recommendation or a prediction of future performance.</p>
 */
@Service
public class InvestmentMatchEngine {

    private static final Logger logger = LoggerFactory.getLogger(InvestmentMatchEngine.class);

    public static final String ENGINE_GROQ = "groq-criteria";
    public static final String ENGINE_FALLBACK = "deterministic-fallback";

    public static final String STATE_MATCH = "MATCH";
    public static final String STATE_MISMATCH = "MISMATCH";
    public static final String STATE_UNVERIFIABLE = "UNVERIFIABLE";
    /** Partial credit: the stored evidence supports the criterion only partly (0 < credit < 1). */
    public static final String STATE_PARTIAL = "PARTIAL";

    public static final String ROLE_MANDATORY = "MANDATORY";
    public static final String ROLE_PREFERENCE = "PREFERENCE";

    public static final String TIER_FULL = "FULL_MATCH";
    public static final String TIER_PARTIAL = "PARTIAL_MATCH";

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
    /**
     * Backend-derived preference criterion (never extracted by the provider): the recorded
     * purchase-to-current price change, graded with partial credit. It differentiates scores
     * using real stored data while being explicitly labelled as history, not a forecast.
     */
    public static final String K_HISTORICAL_PERFORMANCE = "historicalPerformance";

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
            Map.entry(K_VOLATILITY, "Volatility"),
            Map.entry(K_HISTORICAL_PERFORMANCE, "Historical price performance"));

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
            Map.entry(K_VOLATILITY, 20d),
            Map.entry(K_HISTORICAL_PERFORMANCE, 25d));

    private static final Set<String> KNOWN_KEYS = Set.of(
            K_CATEGORY, K_SECTOR, K_RISK, K_HORIZON, K_BUDGET, K_LIQUIDITY,
            K_INCOME_VS_GROWTH, K_RETURN_OBJECTIVE, K_GEOGRAPHY, K_STATUS, K_VOLATILITY);

    private static final Set<String> STRUCTURALLY_UNVERIFIABLE = Set.of(
            K_RISK, K_HORIZON, K_LIQUIDITY, K_VOLATILITY);

    /**
     * Documented grading bounds for the historical price performance criterion: full credit at
     * +100% recorded price change or better (the holding at least doubled), zero credit at
     * -20% or worse, linear in between. These bounds are fixed engine policy, not market data.
     */
    private static final double HISTORICAL_FULL_CREDIT_AT = 100.0;
    private static final double HISTORICAL_ZERO_CREDIT_BELOW = -20.0;

    /**
     * Hard-filter criteria: a verifiable MISMATCH excludes the record, and an unverifiable
     * outcome holds it back. These are all verifiable filter intents (what kind of thing the
     * user wants). Preferences — risk, horizon, liquidity, volatility, return objective — are
     * attributes InvestTrack cannot verify or that look forward, so they only influence
     * ranking and disclosure and never exclude a record.
     */
    private static final Set<String> MANDATORY_KEYS = Set.of(
            K_CATEGORY, K_SECTOR, K_BUDGET, K_GEOGRAPHY, K_STATUS, K_INCOME_VS_GROWTH);

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

    /** Currency token shared by the budget patterns: symbol or word form. */
    private static final String CURRENCY_TOKEN =
            "(?:[$€£₹]|usd|eur|gbp|inr|rs\\.?|dollars?|rupees?)";
    private static final Pattern BUDGET_PATTERN = Pattern.compile(
            "(?:under|below|less than|upto|up to|maximum|max|budget(?: of)?|within|afford(?:able)?|cheaper than)\\s*"
                    + "(" + CURRENCY_TOKEN + ")?\\s*"
                    + "([0-9][0-9,]*(?:\\.[0-9]+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BUDGET_SUFFIX_PATTERN = Pattern.compile(
            "(" + CURRENCY_TOKEN + ")?\\s*"
                    + "([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(?:or less|or below|or under|max|maximum)",
            Pattern.CASE_INSENSITIVE);

    private final GroqService groqService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Compiled lexicon match patterns, cached per synonym. */
    private final Map<String, Pattern> lexiconPatternCache = new ConcurrentHashMap<>();

    public InvestmentMatchEngine(GroqService groqService) {
        this.groqService = groqService;
    }

    // -------------------------------------------------------------------------------------
    // Public model
    // -------------------------------------------------------------------------------------

    /** One extracted, weighted criterion. */
    public record Criterion(String key, String label, double rawWeight, List<String> values) {
    }

    /** The interpretation of one query. */
    public record MatchPlan(String interpretation, boolean investmentRelated, String source,
                            List<Criterion> criteria, String sourceDetail) {
    }

    /** The full outcome of scoring one investment. */
    public record Evaluation(List<AiMatchCriterionDTO> details, int matchPercentage, int dataCoverage,
                             String matchTier, String historicalPerformance,
                             List<String> matchedLabels, List<String> mismatchedLabels,
                             List<String> unverifiableLabels, String primaryReason,
                             boolean missingVerifiableData) {
    }

    /** One investment plus its evaluation, used for sorting and rendering. */
    public record Scored(Investment investment, Evaluation evaluation) {
    }

    // -------------------------------------------------------------------------------------
    // Step 1: criteria extraction
    // -------------------------------------------------------------------------------------

    /**
     * Builds the match plan for a raw user query. Groq is used when it is available;
     * otherwise the documented deterministic extractor runs. Both paths may throw
     * {@link UnrelatedInvestmentQueryException} (HTTP 400) for investment-unrelated requests.
     */
    public MatchPlan buildPlan(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            throw new UnrelatedInvestmentQueryException(
                    "Please enter an investment-related search request.");
        }

        String fallbackDetail = "groq-disabled: no API key configured, deterministic engine used.";
        if (groqService.isEnabled()) {
            GroqService.GenerationOutcome outcome =
                    groqService.generateDetailed(buildCriteriaPrompt(normalized), true);
            if (!outcome.success() && outcome.retryable()) {
                // One bounded retry for transient conditions (quota churn, timeouts, blips).
                logger.info("Groq criteria call failed with transient category {}; retrying once",
                        outcome.errorCategory());
                try {
                    Thread.sleep(750);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                outcome = groqService.generateDetailed(buildCriteriaPrompt(normalized), true);
            }
            if (outcome.success()) {
                try {
                    MatchPlan plan = parseGroqPlan(outcome.text());
                    if (plan != null) {
                        if (!plan.investmentRelated()) {
                            throw new UnrelatedInvestmentQueryException(UNRELATED_MESSAGE);
                        }
                        MatchPlan groqPlan = new MatchPlan(plan.interpretation(), true,
                                ENGINE_GROQ, plan.criteria(),
                                "model " + outcome.model() + ", interpreted in " + outcome.latencyMs() + "ms");
                        groqPlan = withHistoricalPerformance(
                                withDefaultStatusIfOpportunity(normalized, groqPlan));
                        logger.info("Groq extracted {} investment criteria (model={}, latency={}ms)",
                                groqPlan.criteria().size(), outcome.model(), outcome.latencyMs());
                        return groqPlan;
                    }
                    logger.warn("Groq criteria extraction returned unusable JSON; "
                            + "using the deterministic fallback engine");
                    fallbackDetail = "groq returned unusable criteria JSON; deterministic engine used.";
                } catch (UnrelatedInvestmentQueryException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    logger.warn("Groq criteria parsing failed; using the deterministic fallback engine",
                            exception);
                    fallbackDetail = "groq criteria response could not be parsed; deterministic engine used.";
                }
            } else {
                fallbackDetail = "groq unavailable [" + outcome.errorCategory()
                        + (outcome.httpStatus() > 0 ? " HTTP " + outcome.httpStatus() : "")
                        + "]; deterministic engine used.";
                logger.warn("Groq criteria extraction failed: {}", fallbackDetail);
            }
        }

        MatchPlan fallback = fallbackPlan(normalized);
        if (!fallback.investmentRelated()) {
            throw new UnrelatedInvestmentQueryException(UNRELATED_MESSAGE);
        }
        fallback = withHistoricalPerformance(withDefaultStatusIfOpportunity(normalized, fallback));
        logger.info("Fallback engine extracted {} investment criteria (detail: {})",
                fallback.criteria().size(), fallbackDetail);
        return new MatchPlan(fallback.interpretation(), fallback.investmentRelated(),
                ENGINE_FALLBACK, fallback.criteria(), fallbackDetail);
    }

    /**
     * Opportunity-seeking queries ("show me stocks", "find investments", "what should I buy")
     * mean the user wants currently-investable holdings, not their own already-sold positions.
     * When no explicit status was requested and the query is an opportunity search (and not a
     * request for past/sold holdings), a MANDATORY {@code Active} status filter is added and
     * disclosed in the interpretation. This keeps Sold records out of recommendation results
     * without ever hiding them from an explicit historical query.
     */
    private MatchPlan withDefaultStatusIfOpportunity(String query, MatchPlan plan) {
        for (Criterion criterion : plan.criteria()) {
            if (K_STATUS.equals(criterion.key())) {
                return plan; // user stated a status explicitly; respect it
            }
        }
        String text = " " + query.toLowerCase(Locale.ROOT) + " ";
        boolean wantsHistory = containsAnyWord(text, "sold", "already sold", "disposed",
                "exited", "past holding", "past holdings", "historical holding",
                "previous holding", "used to own", "i sold", "my sold");
        if (wantsHistory) {
            return plan;
        }
        boolean opportunityIntent = containsAnyWord(text, "find", "show", "search", "recommend",
                "suggest", "best", "invest", "investment", "buy", "looking for", "want",
                "portfolio", "opportunit", "suitable");
        if (!opportunityIntent) {
            return plan;
        }
        List<Criterion> updated = new ArrayList<>(plan.criteria());
        updated.add(new Criterion(K_STATUS, LABELS.get(K_STATUS), DEFAULT_WEIGHTS.get(K_STATUS),
                List.of("Active")));
        String disclosure = " Currently-investable view: Sold holdings are excluded by default; "
                + "ask about past or sold holdings to include them.";
        String interpretation = plan.interpretation() == null ? "" : plan.interpretation();
        if (!interpretation.contains("Currently-investable view")) {
            interpretation = (interpretation + disclosure).trim();
        }
        return new MatchPlan(interpretation, plan.investmentRelated(), plan.source(),
                List.copyOf(updated), plan.sourceDetail());
    }

    /**
     * When the user states a return objective, the backend derives an additional PREFERENCE
     * criterion — historical price performance — so each record's own stored price data can
     * influence its score instead of every record tying on the same verifiable criteria. The
     * criterion is graded with the documented partial credit and is always labelled as history;
     * the forward-looking return objective itself remains UNVERIFIABLE.
     */
    private MatchPlan withHistoricalPerformance(MatchPlan plan) {
        boolean hasReturnObjective = false;
        for (Criterion criterion : plan.criteria()) {
            if (K_HISTORICAL_PERFORMANCE.equals(criterion.key())) {
                return plan; // already derived
            }
            if (K_RETURN_OBJECTIVE.equals(criterion.key())) {
                hasReturnObjective = true;
            }
        }
        if (!hasReturnObjective) {
            return plan;
        }
        List<Criterion> updated = new ArrayList<>(plan.criteria());
        updated.add(new Criterion(K_HISTORICAL_PERFORMANCE,
                LABELS.get(K_HISTORICAL_PERFORMANCE),
                DEFAULT_WEIGHTS.get(K_HISTORICAL_PERFORMANCE), List.of()));
        return new MatchPlan(plan.interpretation(), plan.investmentRelated(), plan.source(),
                List.copyOf(updated), plan.sourceDetail());
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
                  budget          -> "Budget"                 (verifiable: current price; put the plain number in values[0] and, when the user names a currency, its ISO code - e.g. INR, USD - as values[1])
                  geography       -> "Geographic market"      (verifiable: market, exchange, description; use market names like "India", "United States", "Europe")
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

    private MatchPlan parseGroqPlan(String generated) {
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
                return new MatchPlan(interpretation, false, ENGINE_GROQ, List.of(), null);
            }
            if (interpretation.isBlank() && criteria.isEmpty()) {
                return null;
            }
            return new MatchPlan(interpretation.isBlank()
                    ? "Investment search based on your request." : limit(interpretation, 300),
                    true, ENGINE_GROQ, List.copyOf(criteria), null);
        } catch (Exception exception) {
            logger.warn("Groq criteria JSON could not be parsed: {}", exception.getMessage());
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
     * Deterministic, documented fallback criteria extractor. Used only when Groq is
     * unavailable or returned unusable output; the response is labelled
     * {@link #ENGINE_FALLBACK} so the UI never presents it as Groq-generated.
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

        BudgetExtraction budget = extractBudget(text);
        if (budget != null && budget.amount() != null) {
            List<String> budgetValues = new ArrayList<>();
            budgetValues.add(String.valueOf(budget.amount()));
            if (budget.currency() != null) {
                budgetValues.add(budget.currency());
            }
            criteria.add(new Criterion(K_BUDGET, LABELS.get(K_BUDGET), DEFAULT_WEIGHTS.get(K_BUDGET),
                    budgetValues));
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
                "strong returns", "best performing", "top performing", "outperform", "high growth",
                "potential return", "potential returns", "return potential", "returns potential",
                "growth potential", "strong potential", "high potential")
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
        return new MatchPlan(interpretation, investmentRelated, ENGINE_FALLBACK,
                List.copyOf(criteria), null);
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
        // Descending confirmed-fit percentage, then descending data coverage, then a stable
        // name/symbol/id tiebreak so the order is fully deterministic even when percentages
        // are equal. Each .reversed() is scoped to its own key (reversing a composed chain
        // would invert the whole ordering).
        scored.sort(Comparator
                .comparingInt((Scored item) -> item.evaluation().matchPercentage()).reversed()
                .thenComparing(Comparator
                        .comparingInt((Scored item) -> item.evaluation().dataCoverage()).reversed())
                .thenComparing(item -> safe(item.investment().getName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> safe(item.investment().getSymbol()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Comparator.comparingLong(
                        (Scored item) -> item.investment().getInvestmentId() == null
                                ? Long.MAX_VALUE : item.investment().getInvestmentId())));
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
        double evaluableWeight = 0;
        double earnedWeight = 0;
        boolean missingVerifiableData = false;

        for (Criterion criterion : criteria) {
            CriterionOutcome outcome = evaluateCriterion(criterion, investment);
            String role = roleFor(criterion.key());
            requestedWeight += criterion.rawWeight();
            if (!STATE_UNVERIFIABLE.equals(outcome.state())) {
                evaluableWeight += criterion.rawWeight();
            } else if (!STRUCTURALLY_UNVERIFIABLE.contains(criterion.key())) {
                // The user stated a criterion that stored fields should verify, but this
                // record carries no evidence either way. That is missing data: the record
                // must not be presented as a confirmed match for the stated criterion.
                missingVerifiableData = true;
            }
            double credit = clampCredit(outcome.credit());
            earnedWeight += criterion.rawWeight() * credit;
            details.add(new AiMatchCriterionDTO(criterion.label(), outcome.state(),
                    criterion.rawWeight(), outcome.evidence(), role, credit));
            if (STATE_MISMATCH.equals(outcome.state())) {
                mismatched.add(criterion.label());
            } else if (STATE_UNVERIFIABLE.equals(outcome.state())) {
                unverifiable.add(criterion.label());
            } else {
                // MATCH and PARTIAL both contributed to the score and belong on the card.
                matched.add(criterion.label());
            }
        }

        // Confirmed-fit percentage over the FULL requested weight. Unverifiable criteria stay
        // in the denominator (they contribute 0 to the numerator), so a result can never reach
        // 100% while any stated criterion is unverified. Data coverage reports how much of the
        // request could be evaluated at all.
        int score = requestedWeight <= 0 ? 0
                : (int) Math.round((earnedWeight / requestedWeight) * 100.0);
        int coverage = requestedWeight <= 0 ? 0
                : (int) Math.round((evaluableWeight / requestedWeight) * 100.0);
        int clampedScore = clampPercent(score);
        int clampedCoverage = clampPercent(coverage);

        String tier = (clampedCoverage >= 100 && clampedScore >= 100) ? TIER_FULL : TIER_PARTIAL;
        String historical = buildHistoricalPerformance(investment);

        return new Evaluation(details, clampedScore, clampedCoverage, tier, historical,
                List.copyOf(matched), List.copyOf(mismatched), List.copyOf(unverifiable),
                primaryReason(details, investment), missingVerifiableData);
    }

    /** MANDATORY hard filters versus PREFERENCE ranking criteria. */
    private String roleFor(String key) {
        return MANDATORY_KEYS.contains(key) ? ROLE_MANDATORY : ROLE_PREFERENCE;
    }

    /**
     * Criteria no stored field can decide for any record: risk, horizon, liquidity and
     * volatility are not columns in the database, and a return objective is forward-looking
     * (historical price change is context, never a forecast).
     */
    public boolean isAlwaysUnverifiable(String key) {
        return STRUCTURALLY_UNVERIFIABLE.contains(key) || K_RETURN_OBJECTIVE.equals(key);
    }

    /** True when not a single evaluated record could be checked for this criterion. */
    private boolean everyRecordUnverifiable(List<Scored> ranked, String label) {
        for (Scored item : ranked) {
            for (AiMatchCriterionDTO detail : item.evaluation().details()) {
                if (label.equals(detail.criterion())
                        && !STATE_UNVERIFIABLE.equals(detail.state())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * True when every requested criterion is unverifiable (risk/horizon/liquidity/volatility
     * or a forward-looking return objective). In that case no record can be ranked honestly,
     * so the search returns an empty state with the limitation and a rephrasing suggestion
     * instead of a page of meaningless 0%-matches.
     */
    public boolean noVerifiableCriteria(MatchPlan plan) {
        if (plan.criteria().isEmpty()) {
            return true;
        }
        for (Criterion criterion : plan.criteria()) {
            if (!isAlwaysUnverifiable(criterion.key())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Historical price context, clearly labelled as past performance and never a forecast.
     * Returns {@code null} when the record has no comparable price points.
     */
    private String buildHistoricalPerformance(Investment investment) {
        Double purchase = investment.getPurchasePrice();
        Double current = investment.getCurrentPrice();
        if (purchase == null || current == null || purchase <= 0) {
            return null;
        }
        double change = ((current - purchase) / purchase) * 100.0;
        return String.format(Locale.ROOT,
                "Price change since purchase is %+.1f%% (historical record, not a forecast of future returns).",
                change);
    }

    /**
     * Grades the recorded purchase-to-current price change with the documented partial credit
     * (full at +100% or better, zero at -20% or worse, linear between), so each record's score
     * reflects its own stored data instead of tying with every other record. Records without
     * comparable price points are UNVERIFIABLE for this criterion. The evidence always states
     * that the figure is history and never a forecast of future returns.
     */
    private CriterionOutcome evaluateHistoricalPerformance(Criterion criterion,
                                                           Investment investment) {
        Double purchase = investment.getPurchasePrice();
        Double current = investment.getCurrentPrice();
        if (purchase == null || current == null || purchase <= 0) {
            return new CriterionOutcome(STATE_UNVERIFIABLE,
                    "The record lacks comparable purchase and current prices, so historical "
                            + "price performance could not be calculated for this investment.");
        }
        double change = ((current - purchase) / purchase) * 100.0;
        double credit = clampCredit((change - HISTORICAL_ZERO_CREDIT_BELOW)
                / (HISTORICAL_FULL_CREDIT_AT - HISTORICAL_ZERO_CREDIT_BELOW));
        String state = credit >= 1.0 ? STATE_MATCH
                : credit <= 0.0 ? STATE_MISMATCH : STATE_PARTIAL;
        String evidence = String.format(Locale.ROOT,
                "Recorded price change since purchase is %+.1f%% (history, not a forecast of "
                        + "future returns). Earned %.0f%% of this criterion's weight "
                        + "(full credit at %+.0f%% or better, zero credit at %+.0f%% or worse, "
                        + "linear in between).",
                change, credit * 100.0, HISTORICAL_FULL_CREDIT_AT, HISTORICAL_ZERO_CREDIT_BELOW);
        return new CriterionOutcome(state, evidence, credit);
    }

    /** Keeps criterion credit inside the documented 0..1 range. */
    private static double clampCredit(double credit) {
        if (Double.isNaN(credit) || credit < 0.0) {
            return 0.0;
        }
        return Math.min(1.0, credit);
    }

    /**
     * One criterion's evaluation. {@code credit} is the share of the criterion's weight that
     * the stored evidence earns: 1.0 for MATCH, 0.0 for MISMATCH and UNVERIFIABLE, and a
     * transparent 0..1 factor for PARTIAL (historical price performance).
     */
    private record CriterionOutcome(String state, String evidence, double credit) {
        CriterionOutcome(String state, String evidence) {
            this(state, evidence, STATE_MATCH.equals(state) ? 1.0 : 0.0);
        }
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
            case K_HISTORICAL_PERFORMANCE -> evaluateHistoricalPerformance(criterion, investment);
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
            if (lexiconTextMatch(text, synonym)) {
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
                if (lexiconTextMatch(text, synonym)) {
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
        String requestedCurrency = requestedBudgetCurrency(criterion.values());
        String recordCurrency = normalizeCurrency(investment.getCurrency());
        if (recordCurrency == null) {
            return new CriterionOutcome(STATE_UNVERIFIABLE,
                    "The record does not state its currency, so it cannot be safely compared "
                            + "to the requested limit.");
        }
        if (requestedCurrency != null && !requestedCurrency.equalsIgnoreCase(recordCurrency)) {
            return new CriterionOutcome(STATE_UNVERIFIABLE,
                    "The requested limit is stated in " + requestedCurrency
                            + " but this record is priced in " + recordCurrency
                            + "; currencies are not converted, so the budget could not be verified.");
        }
        Double price = investment.getCurrentPrice();
        if (price == null) {
            return new CriterionOutcome(STATE_UNVERIFIABLE,
                    "The record has no current price, so the budget cannot be checked.");
        }
        String evidence = "Current price " + formatMoney(price, recordCurrency)
                + " per unit versus the requested limit of " + formatMoney(limit, recordCurrency) + ".";
        if (requestedCurrency == null) {
            evidence += " The query did not state a currency; the record's " + recordCurrency
                    + " price was compared as a plain amount.";
        }
        return price <= limit
                ? new CriterionOutcome(STATE_MATCH, evidence)
                : new CriterionOutcome(STATE_MISMATCH, evidence);
    }

    /** Reads the ISO currency code the user stated, wherever the extraction placed it. */
    private String requestedBudgetCurrency(List<String> values) {
        for (String value : values) {
            String currency = normalizeCurrency(value);
            if (currency != null) {
                return currency;
            }
        }
        return null;
    }

    /** Maps a currency symbol, word or ISO code onto an ISO code; null when unknown. */
    private String normalizeCurrency(String token) {
        if (token == null) {
            return null;
        }
        String value = token.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return null;
        }
        if (value.startsWith("₹") || value.equals("inr") || value.startsWith("rs")
                || value.startsWith("rupee")) {
            return "INR";
        }
        if (value.startsWith("$") || value.equals("usd") || value.startsWith("dollar")) {
            return "USD";
        }
        if (value.startsWith("€") || value.equals("eur") || value.startsWith("euro")) {
            return "EUR";
        }
        if (value.startsWith("£") || value.equals("gbp") || value.startsWith("pound")) {
            return "GBP";
        }
        return null;
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
        // Forward-looking return potential is NOT stored and cannot be established from two
        // recorded prices. Historical price change is real information but is not a forecast,
        // so the criterion stays UNVERIFIABLE and the change is reported as labelled context.
        Double purchase = investment.getPurchasePrice();
        Double current = investment.getCurrentPrice();
        if (purchase == null || current == null || purchase <= 0) {
            return new CriterionOutcome(STATE_UNVERIFIABLE,
                    "Future return potential is not stored and cannot be forecast; this record "
                            + "also lacks comparable price points, so no historical change can be shown.");
        }
        double change = ((current - purchase) / purchase) * 100.0;
        return new CriterionOutcome(STATE_UNVERIFIABLE,
                String.format(Locale.ROOT,
                        "Future return potential is not stored and cannot be forecast from two "
                                + "recorded prices. For context, the price change since purchase is %+.1f%% "
                                + "(historical performance, not a prediction).", change));
    }

    // -------------------------------------------------------------------------------------
    // Step 3: response assembly
    // -------------------------------------------------------------------------------------

    /** Builds the overview text actually shown above the results. */
    public String buildOverview(MatchPlan plan, int evaluated, int matched, int excluded,
                                int heldBack, int threshold, int highestScore) {
        String engineNote = ENGINE_FALLBACK.equals(plan.source())
                ? " Groq was unavailable, so the documented deterministic fallback engine was used."
                : " Criteria were interpreted by Groq and scored deterministically in the backend.";
        StringBuilder notes = new StringBuilder();
        if (excluded > 0) {
            notes.append(' ').append(excluded).append(" record")
                    .append(excluded == 1 ? "" : "s")
                    .append(" failed a mandatory criterion and were excluded.");
        }
        if (heldBack > 0) {
            notes.append(' ').append(heldBack).append(" record")
                    .append(heldBack == 1 ? "" : "s")
                    .append(" were held back because the stored data could not verify the criteria "
                            + "needed to rank them, so they are not presented as matches.");
        }
        String excludedNote = notes.toString();
        if (evaluated == 0) {
            return "The investment database contains no records to evaluate." + engineNote;
        }
        if (plan.criteria().isEmpty()) {
            return "Your query did not state any criterion that can be matched against the stored "
                    + "investment fields, so nothing was ranked. " + engineNote;
        }
        if (noVerifiableCriteria(plan)) {
            StringBuilder labels = new StringBuilder();
            for (Criterion criterion : normalizeWeights(plan.criteria())) {
                if (labels.length() > 0) {
                    labels.append(", ");
                }
                labels.append(criterion.label());
            }
            return "None of the stated criteria (" + labels + ") can be verified from InvestTrack's "
                    + "stored investment data, so no record could be scored honestly and nothing "
                    + "was presented as a match. " + engineNote;
        }
        if (matched == 0) {
            return "No investment satisfied every mandatory criterion out of " + evaluated
                    + " evaluated records (highest confirmed-fit score " + highestScore + "%)."
                    + excludedNote + engineNote;
        }
        return "Matched " + matched + " of " + evaluated + " evaluated investments against "
                + plan.criteria().size() + " requested criteria." + excludedNote + " Scores are "
                + "confirmed-fit percentages calculated from stored investment fields — they "
                + "are not predictions, risk ratings or advice." + engineNote;
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
        List<AiCriterionStatusDTO> criteriaStatus = new ArrayList<>();
        List<String> unverified = new ArrayList<>();
        for (Criterion criterion : normalized) {
            requested.add(criterion.label());
            boolean unverifiableNow = isAlwaysUnverifiable(criterion.key());
            // Data-driven availability: a criterion stored fields should be able to answer is
            // still reported unverifiable for this query when not a single evaluated record
            // carried the needed data (e.g. an INR budget against an all-USD database).
            if (!unverifiableNow && !ranked.isEmpty()) {
                unverifiableNow = everyRecordUnverifiable(ranked, criterion.label());
            }
            String state = unverifiableNow ? STATE_UNVERIFIABLE : "VERIFIED";
            criteriaStatus.add(new AiCriterionStatusDTO(criterion.label(), state,
                    roleFor(criterion.key())));
            if (unverifiableNow) {
                unverified.add(criterion.label());
            }
        }

        String objective = plan.interpretation() == null || plan.interpretation().isBlank()
                ? "Investment search based on your request." : plan.interpretation();

        String bestMatches;
        String whyTheyMatch;
        if (matched == 0) {
            int highest = ranked.isEmpty() ? 0 : ranked.get(0).evaluation().matchPercentage();
            bestMatches = "No investment satisfied every mandatory criterion"
                    + (ranked.isEmpty() ? "."
                    : "; the closest candidate confirmed " + highest + "% of the requested weight.");
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
                .append("a risk rating or a recommendation. Data coverage reports how much of your ")
                .append("request could be evaluated at all.");
        if (!unverified.isEmpty()) {
            considerations.append(" Not verifiable from the database: ")
                    .append(String.join(", ", unverified))
                    .append(" — these stay in the score denominator, so no result can reach 100% "
                            + "while they are unverified.");
        }
        for (Criterion criterion : normalized) {
            if (K_HISTORICAL_PERFORMANCE.equals(criterion.key())) {
                considerations.append(" Historical price performance is graded from your stored "
                        + "purchase and current prices (full credit at +100% or better, zero at "
                        + "-20% or worse, linear between). It describes recorded past results "
                        + "only and does not establish future returns.");
                break;
            }
        }
        if (plan.criteria().isEmpty()) {
            considerations.append(" The query did not state any matchable criterion.");
        }
        considerations.append(" ").append(evaluated)
                .append(" eligible investment records were evaluated for this query.");

        String refine;
        if (plan.criteria().isEmpty()) {
            refine = "Your question did not state a risk tolerance, time horizon, budget, market "
                    + "or goal, so nothing could be ranked objectively. Add at least one concrete "
                    + "criterion, for example \"technology stocks under $500 with Active status\".";
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
                considerations.toString(), refine, List.copyOf(criteriaStatus),
                List.copyOf(requested), List.copyOf(unverified));
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

    /**
     * Builds the concise, investment-specific explanation shown on each card: the confirmed-fit
     * percentage, every criterion that contributed credit (full or partial) with its stored
     * evidence, the first contradicted criterion, and every criterion that could not be
     * verified — so the card always explains its own number.
     */
    public String buildExplanation(Evaluation evaluation) {
        StringBuilder builder = new StringBuilder();
        builder.append("Match ").append(evaluation.matchPercentage())
                .append("% (data coverage ").append(evaluation.dataCoverage())
                .append("%, ").append(TIER_FULL.equals(evaluation.matchTier())
                        ? "fully verified" : "partial match").append(").");
        int used = 0;
        for (AiMatchCriterionDTO detail : evaluation.details()) {
            if (detail.credit() <= 0) {
                continue;
            }
            builder.append(' ').append(detail.criterion()).append(": ").append(detail.evidence());
            if (++used >= 3) {
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
            builder.append(" Not earned (not verifiable from stored data): ")
                    .append(String.join(", ", evaluation.unverifiableLabels()))
                    .append(" — each stays in the score denominator at zero credit.");
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
        String text = " " + queryText.toLowerCase(Locale.ROOT) + " ";
        for (Map.Entry<String, List<String>> entry : lexicon.entrySet()) {
            for (String synonym : entry.getValue()) {
                if (lexiconTextMatch(text, synonym)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * Word-boundary-aware lexicon matching. Short synonyms such as "nse", "bse" or "us"
     * must match whole words only, so unrelated text like "expense ratio" or "absent"
     * never counts as geographic or sector evidence. Longer tokens match from a word
     * start so documented stems ("technolog", "utilit") keep working. Synonyms that
     * contain punctuation ("u.s.") require exact space-padded containment. Matches are
     * cached per synonym.
     */
    private boolean lexiconTextMatch(String paddedLowerText, String synonym) {
        if (paddedLowerText == null || synonym == null) {
            return false;
        }
        String token = synonym.toLowerCase(Locale.ROOT).trim();
        if (token.isEmpty()) {
            return false;
        }
        if (!token.matches("[a-z]+(?:\\s+[a-z]+)*")) {
            return paddedLowerText.contains(" " + token + " ")
                    || paddedLowerText.contains(token);
        }
        Pattern pattern = lexiconPatternCache.computeIfAbsent(token, key -> {
            String[] words = key.split("\\s+");
            StringBuilder regex = new StringBuilder("\\b");
            for (int i = 0; i < words.length; i++) {
                if (i > 0) {
                    regex.append("\\s+");
                }
                regex.append(Pattern.quote(words[i]));
                if (key.length() <= 5 && i == words.length - 1) {
                    // Short synonyms also match their plural ("stocks" -> "stock" style).
                    regex.append("(?:s)?");
                }
            }
            regex.append("\\b");
            return Pattern.compile(regex.toString());
        });
        return pattern.matcher(paddedLowerText).find();
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

    /** A budget read from the query: the numeric limit and the stated currency, if any. */
    public record BudgetExtraction(Double amount, String currency) {
    }

    private BudgetExtraction extractBudget(String text) {
        Matcher matcher = BUDGET_PATTERN.matcher(text);
        if (matcher.find()) {
            return toBudget(matcher.group(1), matcher.group(2));
        }
        matcher = BUDGET_SUFFIX_PATTERN.matcher(text);
        if (matcher.find()) {
            return toBudget(matcher.group(1), matcher.group(2));
        }
        return null;
    }

    private BudgetExtraction toBudget(String currencyToken, String numberToken) {
        Double amount = null;
        if (numberToken != null) {
            try {
                amount = Double.parseDouble(numberToken.replace(",", ""));
            } catch (NumberFormatException ignored) {
                amount = null;
            }
        }
        return new BudgetExtraction(amount, normalizeCurrency(currencyToken));
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
