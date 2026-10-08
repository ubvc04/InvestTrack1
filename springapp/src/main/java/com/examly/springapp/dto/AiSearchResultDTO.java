package com.examly.springapp.dto;

import java.util.List;

public record AiSearchResultDTO(
        InvestmentDTO investment,
        double relevanceScore,
        List<String> matchedFactors,
        String explanation) {
}
