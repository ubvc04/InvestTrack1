package com.examly.springapp.dto;

import java.util.List;

/**
 * AI search response with deterministic match percentages and an analysis panel.
 *
 * @param semanticRankingUsed {@code true} only when Groq actually interpreted the query;
 *                            {@code false} when the documented deterministic fallback engine ran
 * @param analysis            overall suggestion / analysis panel content
 * @param threshold           configured minimum match percentage (from configuration)
 * @param evaluatedCount      every eligible investment that was scored (not just the returned page)
 * @param matchedCount        investments that reached the threshold (size of {@code results})
 * @param engine              {@code "groq-criteria"} or {@code "deterministic-fallback"}
 * @param excludedCount       records that verifiably contradicted at least one stated criterion
 *                            and were excluded before ranking (mandatory criteria)
 * @param heldBackCount       records held back because the stored data could not verify a
 *                            stated verifiable criterion (they are never presented as matches)
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
        String engine,
        int excludedCount,
        int heldBackCount,
        String engineDetail) {
}
