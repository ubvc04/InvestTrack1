package com.examly.springapp.dto;

import java.util.List;

/**
 * Overall AI analysis panel content for one AI search.
 *
 * <p>Every string is derived from the actual query, the criteria extracted for it and
 * the evaluations that were really performed against the MySQL investment records.
 * No market facts, risk ratings or return guarantees are invented here.</p>
 *
 * @param objective         short interpretation of what the user asked for
 * @param bestMatches       summary of the highest-scoring investments that passed the filters
 * @param whyTheyMatch      the criteria that drove the top scores
 * @param thingsToConsider  limitations, missing data and trade-offs
 * @param refineSuggestion  optional advice for making the query more specific
 * @param criteriaStatus    truthful per-criterion status (label + verified/unverifiable + role)
 *                          — the single source the UI renders, so a criterion can never be
 *                          displayed as verified when the backend could not verify it
 * @param requestedCriteria labels of the criteria extracted from the query
 * @param unverifiedCriteria labels of the requested criteria the database cannot verify
 */
public record AiAnalysisDTO(
        String objective,
        String bestMatches,
        String whyTheyMatch,
        String thingsToConsider,
        String refineSuggestion,
        List<AiCriterionStatusDTO> criteriaStatus,
        List<String> requestedCriteria,
        List<String> unverifiedCriteria) {
}
