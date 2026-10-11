package com.examly.springapp.service;

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
import java.util.Map;

/**
 * AI investment search with deterministic, explainable match percentages.
 *
 * <p>The service loads every eligible investment from MySQL, asks
 * {@link InvestmentMatchEngine} to extract the criteria stated in the raw user query
 * (Gemini when available, documented deterministic fallback otherwise), scores every single
 * record against those criteria, sorts descending and applies the configured match threshold.</p>
 *
 * <p>Configuration:</p>
 * <ul>
 *   <li>{@code ai.match.threshold} (default 50) – minimum match percentage for a record to be
 *       returned in the primary AI-matched results.</li>
 * </ul>
 */
@Service
public class AiMatchService {

    private static final Logger logger = LoggerFactory.getLogger(AiMatchService.class);

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

        // Mandatory criteria + threshold. A record that verifiably contradicts any stated
        // criterion fails a mandatory filter and is excluded before ranking. A record whose
        // stored data cannot verify a stated verifiable criterion (e.g. no market evidence
        // for a geographic request) is held back rather than presented as a match. A missing
        // database field is never treated as a match or a mismatch. Structurally unverifiable
        // criteria (risk, horizon, liquidity, volatility) never block results - they only
        // lower confidence and are disclosed in the analysis panel.
        List<InvestmentMatchEngine.Scored> passing = new ArrayList<>();
        Map<String, Integer> exclusionReasons = new LinkedHashMap<>();
        int excluded = 0;
        int heldBack = 0;
        for (InvestmentMatchEngine.Scored item : ranked) {
            boolean failedMandatory = false;
            for (com.examly.springapp.dto.AiMatchCriterionDTO detail : item.evaluation().details()) {
                if (InvestmentMatchEngine.STATE_MISMATCH.equals(detail.state())) {
                    failedMandatory = true;
                    exclusionReasons.merge(detail.criterion(), 1, Integer::sum);
                }
            }
            if (failedMandatory) {
                excluded++;
                continue;
            }
            if (item.evaluation().missingVerifiableData()) {
                heldBack++;
                continue;
            }
            if (item.evaluation().matchPercentage() >= matchThreshold) {
                passing.add(item);
            }
        }
        List<AiSearchResultDTO> results = new ArrayList<>(passing.size());
        for (InvestmentMatchEngine.Scored item : passing) {
            results.add(toResult(item, true));
        }

        int highestScore = ranked.isEmpty() ? 0 : ranked.get(0).evaluation().matchPercentage();
        String overview = matchEngine.buildOverview(plan, investments.size(), results.size(),
                excluded, heldBack, matchThreshold, highestScore);
        var analysis = matchEngine.buildAnalysis(plan, ranked, passing, matchThreshold,
                investments.size());

        long latency = System.currentTimeMillis() - started;
        // Log correlation data only: never the raw query, never credentials.
        logger.info("AI match search [len={}, hash={}]: engine={}, detail=\"{}\", evaluated={}, "
                        + "matched={}, excluded={}, heldBack={}, threshold={}, latency={}ms, "
                        + "exclusionReasons={}",
                query == null ? 0 : query.length(),
                query == null ? "-" : Integer.toHexString(query.hashCode()),
                plan.source(), plan.sourceDetail(), investments.size(), results.size(), excluded,
                heldBack, matchThreshold, latency, exclusionReasons);

        return new AiSearchResponseDTO(
                query,
                overview,
                InvestmentMatchEngine.ENGINE_GEMINI.equals(plan.source()),
                results,
                analysis,
                matchThreshold,
                investments.size(),
                results.size(),
                plan.source(),
                excluded,
                heldBack,
                plan.sourceDetail());
    }

    private AiSearchResultDTO toResult(InvestmentMatchEngine.Scored item, boolean passedThreshold) {
        var evaluation = item.evaluation();
        double relevanceScore = Math.round(evaluation.matchPercentage()) / 100.0;
        return new AiSearchResultDTO(
                ApiDtoMapper.toInvestmentResponse(item.investment()),
                relevanceScore,
                evaluation.matchedLabels(),
                matchEngine.buildExplanation(evaluation),
                evaluation.matchPercentage(),
                evaluation.confidence(),
                evaluation.details(),
                passedThreshold);
    }
}
