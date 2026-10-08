package com.examly.springapp.dto;

import java.util.List;

public record AiSearchResponseDTO(
        String query,
        String overview,
        boolean semanticRankingUsed,
        List<AiSearchResultDTO> results) {
}
