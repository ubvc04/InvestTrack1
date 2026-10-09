package com.examly.springapp.dto;

import java.util.List;

/**
 * A single AI-ranked investment.
 *
 * @param relevanceScore   match percentage expressed as a 0..1 fraction ({@code matchPercentage / 100})
 * @param matchPercentage  deterministic match percentage, 0..100
 * @param confidence       share of the requested criterion weight that could actually be
 *                         verified from the database, 0..100
 * @param criteria         per-criterion outcome used to calculate the score
 * @param passedThreshold  whether this result cleared the configured match threshold
 */
public record AiSearchResultDTO(
        InvestmentDTO investment,
        double relevanceScore,
        List<String> matchedFactors,
        String explanation,
        int matchPercentage,
        int confidence,
        List<AiMatchCriterionDTO> criteria,
        boolean passedThreshold) {
}
