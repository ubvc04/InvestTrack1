package com.examly.springapp.controller;

import com.examly.springapp.dto.AiSearchRequestDTO;
import com.examly.springapp.dto.AiSearchResponseDTO;
import com.examly.springapp.dto.ApiDtoMapper;
import com.examly.springapp.dto.InvestmentDTO;
import com.examly.springapp.service.AiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "http://localhost:8081")
public class AiController {
    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/search")
    public ResponseEntity<List<InvestmentDTO>> searchInvestments(
            @RequestBody AiSearchRequestDTO request) {
        String query = request == null ? "" : request.query();
        return ResponseEntity.ok(aiService.searchInvestments(query).stream()
                .map(ApiDtoMapper::toInvestmentResponse).toList());
    }

    /**
     * Grounded, ranked AI search over the investments persisted in MySQL.
     * Returns the original query, an explanation, and results whose matching
     * scores come from the actual ranking process (Gemini ranking when the
     * API key is configured, deterministic fallback ranking otherwise).
     */
    @PostMapping("/search/detailed")
    public ResponseEntity<AiSearchResponseDTO> searchInvestmentsDetailed(
            @RequestBody AiSearchRequestDTO request) {
        String query = request == null ? "" : request.query();
        return ResponseEntity.ok(aiService.searchDetailed(query));
    }
}
