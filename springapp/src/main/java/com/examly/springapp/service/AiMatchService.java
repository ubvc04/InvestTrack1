package com.examly.springapp.service;

import com.examly.springapp.dto.AiMatchCriterionDTO;
import com.examly.springapp.dto.AiSearchResultDTO;
import com.examly.springapp.dto.AiSearchResponseDTO;
import com.examly.springapp.dto.ApiDtoMapper;
import com.examly.springapp.model.Investment;
import com.examly.springapp.repository.InvestmentRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI investment search with deterministic, explainable match percentages.
 *
 * <p>The service loads every eligible investment from MySQL, asks
 * {@link InvestmentMatchEngine} to extract the criteria stated in the raw user query
 * (Groq when available, documented deterministic fallback otherwise), scores every single
 * record against those criteria and sorts descending.</p>
 *
 * <p><b>Eligibility vs. ranking.</b> A record is returned when it satisfies every
 * <i>mandatory</i> criterion (instrument type, sector, budget, geographic market, investment
 * status): a verifiable mismatch excludes it, and data that cannot verify a mandatory
 * criterion holds it back instead of being shown as a match. Preference criteria (risk,
 * horizon, liquidity, volatility, income-vs-growth, return objective) never exclude a record —
 * they only influence the score. The configured threshold does not hide eligible records; it
 * labels a result a "strong match" (see {@code ai.match.threshold}).</p>
 *
 * <p>Every record lands in exactly one bucket, so the reported counts are additive:
 * {@code evaluated = matched + excluded + heldBack}.</p>
 *
 * <p>Configuration:</p>
 * <ul>
 *   <li>{@code ai.match.threshold} (default 50) – confirmed-fit score at or above which a
 *       result is additionally labelled a strong match.</li>
 * </ul>
 */
@Service
public class AiMatchService {

    private static final Logger logger = LoggerFactory.getLogger(AiMatchService.class);

    /** Documented scoring formula surfaced verbatim in the API response and the UI. */
    public static final String MATCH_SCORE_DEFINITION =
            "Match % = (weight of criteria the backend confirmed satisfied) / (total weight of all "
                    + "requested criteria) x 100. Criteria earn a transparent credit of 0..1 of "
                    + "their weight: 1 for a confirmed match, 0 for a mismatch or for criteria the "
                    + "stored data cannot verify (which stay in the denominator, so no result can "
                    + "reach 100% while a stated criterion is unverified), and a documented "
                    + "0..1 factor for partial evidence — historical price performance is graded "
                    + "linearly, full credit at +100% recorded price change or better, zero at "
                    + "-20% or worse. Data coverage % = (weight of criteria the stored data could "
                    + "evaluate) / (total weight) x 100. Scores are computed only from stored "
                    + "investment fields and are not predictions, risk ratings or advice.";

    private static final String DATA_SOURCE_LIMITATION =
            "All figures come from your stored investment records. InvestTrack does not fetch live "
                    + "market data, analyst risk ratings or return forecasts.";

    /** Corporate-name noise stripped only for *detecting* possible duplicates — never for merging. */
    private static final List<String> NAME_SUFFIXES = List.of(
            "incorporated", "inc", "corp", "corporation", "ltd", "limited", "llc", "plc",
            "co", "company", "group", "holdings", "holding", "sa", "ag", "nv", "the");

    private final InvestmentRepo investmentRepository;
    private final InvestmentMatchEngine matchEngine;
    private final int matchThreshold;

    public AiMatchService(InvestmentRepo investmentRepository,
                          InvestmentMatchEngine matchEngine,
                          @Value("${ai.match.threshold:50}") int matchThreshold) {
        this.investmentRepository = investmentRepository;
        this.matchEngine = matchEngine;
        this.matchThreshold = Math.max(0, Math.min(100, matchThreshold));
    }

    public int getMatchThreshold() {
        return matchThreshold;
    }

    /**
     * Runs the complete AI matching workflow for one raw query.
     *
     * <p>Every eligible investment is evaluated — the whole table, not a first page — so no
     * record can be silently omitted because of batching. Scores are reproducible for the same
     * query against unchanged data.</p>
     *
     * @throws com.examly.springapp.exceptions.UnrelatedInvestmentQueryException (HTTP 400) when the
     *         query is not investment-related
     */
    public AiSearchResponseDTO search(String query) {
        long started = System.currentTimeMillis();
        InvestmentMatchEngine.MatchPlan plan = matchEngine.buildPlan(query);
        List<Investment> investments = investmentRepository.findAll();

        List<InvestmentMatchEngine.Scored> ranked = matchEngine.rank(plan, investments);

        // When every requested criterion is unverifiable (risk/horizon/liquidity/volatility or a
        // forward-looking return objective), no record can be ranked honestly. Report an empty
        // state with the limitation instead of a page of meaningless 0%-matches.
        boolean noVerifiableCriteria = matchEngine.noVerifiableCriteria(plan);

        // Mandatory criteria gate eligibility; preferences only rank. Every evaluated record is
        // counted exactly once: excluded (mandatory mismatch) > held back (mandatory criterion
        // unverifiable for that record) > matched (all mandatory criteria satisfied). Unverified
        // preference criteria (e.g. risk) never block a record — they lower its score and its
        // data coverage and are disclosed per result and per query.
        List<InvestmentMatchEngine.Scored> passing = new ArrayList<>();
        Map<String, Integer> exclusionReasons = new LinkedHashMap<>();
        int excluded = 0;
        int heldBack = 0;
        for (InvestmentMatchEngine.Scored item : ranked) {
            if (noVerifiableCriteria) {
                heldBack++;
                continue;
            }
            boolean failedMandatory = false;
            boolean missingMandatoryData = false;
            for (AiMatchCriterionDTO detail : item.evaluation().details()) {
                if (!InvestmentMatchEngine.ROLE_MANDATORY.equals(detail.role())) {
                    continue;
                }
                if (InvestmentMatchEngine.STATE_MISMATCH.equals(detail.state())) {
                    failedMandatory = true;
                    exclusionReasons.merge(detail.criterion(), 1, Integer::sum);
                } else if (InvestmentMatchEngine.STATE_UNVERIFIABLE.equals(detail.state())) {
                    missingMandatoryData = true;
                }
            }
            if (failedMandatory) {
                excluded++;
                continue;
            }
            if (missingMandatoryData) {
                heldBack++;
                continue;
            }
            passing.add(item);
        }
        List<AiSearchResultDTO> results = new ArrayList<>(passing.size());
        for (InvestmentMatchEngine.Scored item : passing) {
            results.add(toResult(item));
        }
        long fullMatches = results.stream()
                .filter(result -> InvestmentMatchEngine.TIER_FULL.equals(result.matchTier()))
                .count();
        long strongMatches = results.stream().filter(AiSearchResultDTO::passedThreshold).count();

        int highestScore = ranked.isEmpty() ? 0 : ranked.get(0).evaluation().matchPercentage();
        String overview = matchEngine.buildOverview(plan, investments.size(), results.size(),
                excluded, heldBack, matchThreshold, highestScore);
        if (!results.isEmpty()) {
            overview += " " + strongMatches + " of " + results.size()
                    + " matches also reached the " + matchThreshold + "% strong-match line; the "
                    + "rest are shown as partial matches because they satisfy every mandatory "
                    + "criterion.";
        }
        var analysis = matchEngine.buildAnalysis(plan, ranked, passing, matchThreshold,
                investments.size());
        List<String> limitations = buildLimitations(plan, analysis.unverifiedCriteria(), results);

        long latency = System.currentTimeMillis() - started;
        // Log correlation data only: never the raw query, never credentials.
        logger.info("AI match search [len={}, hash={}]: engine={}, detail=\"{}\", evaluated={}, "
                        + "matched={}, fullMatches={}, excluded={}, heldBack={}, threshold={}, "
                        + "latency={}ms, exclusionReasons={}",
                query == null ? 0 : query.length(),
                query == null ? "-" : Integer.toHexString(query.hashCode()),
                plan.source(), plan.sourceDetail(), investments.size(), results.size(),
                fullMatches, excluded, heldBack, matchThreshold, latency, exclusionReasons);

        return new AiSearchResponseDTO(
                query,
                overview,
                InvestmentMatchEngine.ENGINE_GROQ.equals(plan.source()),
                results,
                analysis,
                matchThreshold,
                investments.size(),
                results.size(),
                (int) fullMatches,
                plan.source(),
                excluded,
                heldBack,
                limitations,
                MATCH_SCORE_DEFINITION,
                plan.sourceDetail());
    }

    /**
     * Honest, human-readable limitations for this query: what the stored data can and cannot
     * answer, which requested criteria stayed unverified, engine fallback status and possible
     * duplicate securities (reported, never merged or deleted).
     */
    private List<String> buildLimitations(InvestmentMatchEngine.MatchPlan plan,
                                          List<String> unverifiedCriteria,
                                          List<AiSearchResultDTO> results) {
        List<String> limitations = new ArrayList<>();
        if (!InvestmentMatchEngine.ENGINE_GROQ.equals(plan.source())) {
            limitations.add("Groq was unavailable, so the criteria were parsed by the "
                    + "deterministic fallback interpreter; scoring is identical either way.");
        }
        limitations.add(DATA_SOURCE_LIMITATION);
        for (String label : unverifiedCriteria) {
            limitations.add(label + ": not verifiable from stored data — reported as unverified, "
                    + "never counted as a match, and it lowers the match score and data coverage.");
        }
        boolean historicalRequested = plan.criteria().stream().anyMatch(
                criterion -> InvestmentMatchEngine.K_HISTORICAL_PERFORMANCE.equals(criterion.key()));
        if (historicalRequested) {
            limitations.add("Historical price performance is calculated from your stored purchase "
                    + "and current prices. It describes recorded past results only and does not "
                    + "establish future returns; the forward-looking return objective remains "
                    + "unverified.");
        }
        String duplicates = describePossibleDuplicates(results);
        if (duplicates != null) {
            limitations.add(duplicates);
        }
        return List.copyOf(limitations);
    }

    /**
     * Detects results that may refer to the same security (identical ticker, or names equal
     * after stripping corporate suffixes) and reports them. Records are never merged, renamed
     * or deleted: separate holdings stay separate.
     */
    private String describePossibleDuplicates(List<AiSearchResultDTO> results) {
        Map<String, List<String>> byName = new LinkedHashMap<>();
        Map<String, List<String>> bySymbol = new LinkedHashMap<>();
        for (AiSearchResultDTO result : results) {
            var investment = result.investment();
            if (investment == null) {
                continue;
            }
            String display = investment.symbol() != null && !investment.symbol().isBlank()
                    ? investment.name() + " (" + investment.symbol() + ")"
                    : investment.name();
            String normalizedName = normalizeSecurityName(investment.name());
            if (!normalizedName.isEmpty()) {
                byName.computeIfAbsent(normalizedName, key -> new ArrayList<>()).add(display);
            }
            if (investment.symbol() != null && !investment.symbol().isBlank()) {
                bySymbol.computeIfAbsent(investment.symbol().trim().toUpperCase(Locale.ROOT),
                        key -> new ArrayList<>()).add(display);
            }
        }
        List<String> groups = new ArrayList<>();
        collectDuplicates(byName, groups);
        collectDuplicates(bySymbol, groups);
        if (groups.isEmpty()) {
            return null;
        }
        return "Possible duplicate securities among the results: " + String.join("; ", groups)
                + ". They are reported here only — records are kept separate and never merged "
                + "or deleted; compare symbol and exchange to tell them apart.";
    }

    private void collectDuplicates(Map<String, List<String>> groups, List<String> out) {
        for (List<String> names : groups.values()) {
            if (names.size() > 1) {
                out.add(String.join(" / ", names));
            }
        }
    }

    private static String normalizeSecurityName(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        StringBuilder builder = new StringBuilder();
        for (String token : cleaned.split("\\s+")) {
            if (!NAME_SUFFIXES.contains(token)) {
                builder.append(token).append(' ');
            }
        }
        return builder.toString().trim();
    }

    private AiSearchResultDTO toResult(InvestmentMatchEngine.Scored item) {
        var evaluation = item.evaluation();
        double relevanceScore = Math.round(evaluation.matchPercentage()) / 100.0;
        return new AiSearchResultDTO(
                ApiDtoMapper.toInvestmentResponse(item.investment()),
                relevanceScore,
                evaluation.matchedLabels(),
                matchEngine.buildExplanation(evaluation),
                evaluation.matchPercentage(),
                evaluation.dataCoverage(),
                evaluation.matchTier(),
                evaluation.historicalPerformance(),
                evaluation.details(),
                evaluation.matchPercentage() >= matchThreshold);
    }
}
