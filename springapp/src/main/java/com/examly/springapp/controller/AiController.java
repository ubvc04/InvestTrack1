package com.examly.springapp.controller;

import com.examly.springapp.dto.AiSearchRequestDTO;
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
}
