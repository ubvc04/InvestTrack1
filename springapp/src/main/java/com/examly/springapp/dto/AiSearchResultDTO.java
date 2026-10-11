package com.examly.springapp.dto;

import java.util.List;

/**
 * A single AI-ranked investment.
 *
 * @param relevanceScore   match percentage expressed as a 0..1 fraction ({@code matchPercentage / 100})
 * @param matchPercentage  deterministic confirmed-fit percentage, 0..100. Calculated by the
 *                         backend as {@code earned criterion weight / total requested criterion
 *                         weight}. Unverifiable criteria stay in the denominator, so a result can
 *                         never score 100% while a stated criterion is unverified.
 * @param dataCoverage     share of the requested criterion weight that could actually be
 *                         evaluated from the database, 0..100 (replaces the old, misleading
 *                         "confidence"). Not a statistical confidence interval.
 * @param matchTier        {@code FULL_MATCH} only when every requested criterion was verified and
 *                         satisfied; otherwise {@code PARTIAL_MATCH} (some requested criteria could
 *                         not be verified from stored data)
 * @param historicalPerformance clearly-labelled historical context (e.g. price change since
 *                         purchase) or {@code null}. Never a forecast.
 * @param criteria         per-criterion outcome used to calculate the score
 * @param passedThreshold  whether the confirmed-fit score cleared the configured threshold
 */
public record AiSearchResultDTO(
        InvestmentDTO investment,
        double relevanceScore,
        List<String> matchedFactors,
        String explanation,
        int matchPercentage,
        int dataCoverage,
        String matchTier,
        String historicalPerformance,
        List<AiMatchCriterionDTO> criteria,
        boolean passedThreshold) {
}
