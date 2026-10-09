package com.examly.springapp.dto;

import java.util.List;

/**
 * AI search response with deterministic match percentages and an analysis panel.
 *
 * @param semanticRankingUsed {@code true} only when Gemini actually interpreted the query;
 *                            {@code false} when the documented deterministic fallback engine ran
 * @param analysis            overall suggestion / analysis panel content
 * @param threshold           configured minimum match percentage (from configuration)
 * @param evaluatedCount      every eligible investment that was scored (not just the returned page)
 * @param matchedCount        investments that reached the threshold (size of {@code results})
 * @param engine              {@code "gemini-criteria"} or {@code "deterministic-fallback"}
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
        String engine) {
}
