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
import java.util.List;

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
        InvestmentMatchEngine.MatchPlan plan = matchEngine.buildPlan(query);
        List<Investment> investments = investmentRepository.findAll();

        List<InvestmentMatchEngine.Scored> ranked = matchEngine.rank(plan, investments);

        // Primary AI-matched results: only investments that reached the configured threshold.
        List<InvestmentMatchEngine.Scored> passing = new ArrayList<>();
        for (InvestmentMatchEngine.Scored item : ranked) {
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
                matchThreshold, highestScore);
        var analysis = matchEngine.buildAnalysis(plan, ranked, passing, matchThreshold,
                investments.size());

        logger.info("AI match search [{}]: evaluated={}, matched={}, threshold={}, engine={}",
                query, investments.size(), results.size(), matchThreshold, plan.source());

        return new AiSearchResponseDTO(
                query,
                overview,
                InvestmentMatchEngine.ENGINE_GEMINI.equals(plan.source()),
                results,
                analysis,
                matchThreshold,
                investments.size(),
                results.size(),
                plan.source());
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
