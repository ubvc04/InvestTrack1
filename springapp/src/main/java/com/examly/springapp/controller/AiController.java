package com.examly.springapp.controller;

import com.examly.springapp.dto.AiDiagnosticsDTO;
import com.examly.springapp.dto.AiSearchRequestDTO;
import com.examly.springapp.dto.AiSearchResponseDTO;
import com.examly.springapp.dto.ApiDtoMapper;
import com.examly.springapp.dto.InvestmentDTO;
import com.examly.springapp.service.AiDiagnosticsService;
import com.examly.springapp.service.AiMatchService;
import com.examly.springapp.service.AiService;
import com.examly.springapp.service.GroqService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "http://localhost:8081")
public class AiController {
    private final AiService aiService;
    private final AiMatchService aiMatchService;
    private final GroqService groqService;
    private final AiDiagnosticsService aiDiagnosticsService;

    public AiController(AiService aiService, AiMatchService aiMatchService,
                        GroqService groqService, AiDiagnosticsService aiDiagnosticsService) {
        this.aiService = aiService;
        this.aiMatchService = aiMatchService;
        this.groqService = groqService;
        this.aiDiagnosticsService = aiDiagnosticsService;
    }

    @PostMapping("/search")
    public ResponseEntity<List<InvestmentDTO>> searchInvestments(
            @RequestBody AiSearchRequestDTO request) {
        String query = request == null ? "" : request.query();
        return ResponseEntity.ok(aiService.searchInvestments(query).stream()
                .map(ApiDtoMapper::toInvestmentResponse).toList());
    }

    /**
     * AI investment search with deterministic match percentages.
     *
     * <p>Groq interprets the unmodified query and extracts weighted criteria (a documented
     * deterministic engine takes over when Groq is unavailable), the backend scores every
     * eligible investment in MySQL against those criteria, and the response contains the ranked
     * results, per-criterion explanations, the configured threshold and the analysis panel
     * content. Scores come from the matching process itself — never from Groq's arithmetic
     * and never from hardcoded values.</p>
     */
    @PostMapping("/search/detailed")
    public ResponseEntity<AiSearchResponseDTO> searchInvestmentsDetailed(
            @RequestBody AiSearchRequestDTO request) {
        String query = request == null ? "" : request.query();
        return ResponseEntity.ok(aiMatchService.search(query));
    }

    /**
     * Live Groq connectivity probe (administrators only). Verifies the key is
     * configured, sends one small real request to the configured model and reports the
     * round-trip time plus a sanitized error category on failure. The API key itself is
     * never returned or logged.
     */
    @GetMapping("/health")
    public ResponseEntity<GroqService.HealthReport> aiHealth() {
        return ResponseEntity.ok(groqService.healthCheck());
    }

    /**
     * Runs the full AI agent test suite (A-H) against the live database through the same
     * pipeline the UI uses, plus backend and Groq connectivity checks. Administrators only.
     */
    @PostMapping("/diagnostics")
    public ResponseEntity<AiDiagnosticsDTO> aiDiagnostics() {
        return ResponseEntity.ok(aiDiagnosticsService.runAll());
    }
}
