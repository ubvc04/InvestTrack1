package com.examly.springapp.dto;

import java.util.List;

/**
 * AI search response with deterministic match percentages and an analysis panel.
 *
 * @param semanticRankingUsed {@code true} only when Groq actually interpreted the query;
 *                            {@code false} when the documented deterministic fallback engine ran
 * @param analysis            overall suggestion / analysis panel content
 * @param threshold           configured "strong match" score line (from configuration); records
 *                            that pass the mandatory filters are always shown and ranked, the
 *                            threshold only labels a result a strong match
 * @param evaluatedCount      every investment loaded and scored for this query
 * @param matchedCount        investments that satisfied every mandatory criterion (size of
 *                            {@code results})
 * @param fullMatchCount      results where every requested criterion was verified and satisfied
 *                            ({@code matchTier = FULL_MATCH}); the rest are partial matches
 * @param engine              {@code "groq-criteria"} or {@code "deterministic-fallback"}
 * @param excludedCount       records that verifiably contradicted at least one MANDATORY criterion
 *                            and were excluded before ranking
 * @param heldBackCount       records held back because the stored data could not verify a
 *                            MANDATORY criterion for them (they are never presented as matches)
 * @param limitations         honest, human-readable data limitations for this query (e.g.
 *                            unverifiable risk tolerance or forward-looking return potential)
 * @param matchScoreDefinition the documented scoring formula shown in the UI
 * @param engineDetail        sanitized detail about the interpretation engine, e.g. the model
 *                            used and its latency, or the reason the fallback engine ran;
 *                            never contains credentials
 */
public record AiSearchResponseDTO(
        String query,
        String overview,
        boolean semanticRankingUsed,
        List<AiSearchResultDTO> results,
        AiAnalysisDTO analysis,
        int threshold,
        int evaluatedCount,
        int matchedCount,
        int fullMatchCount,
        String engine,
        int excludedCount,
        int heldBackCount,
        List<String> limitations,
        String matchScoreDefinition,
        String engineDetail) {
}
