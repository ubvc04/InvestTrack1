package com.examly.springapp.controller;

import com.examly.springapp.dto.ApiDtoMapper;
import com.examly.springapp.dto.InvestmentDTO;
import com.examly.springapp.service.InvestmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/investments")
@CrossOrigin(origins = "http://localhost:8081")
public class InvestmentController {
    private final InvestmentService investmentService;

    public InvestmentController(InvestmentService investmentService) {
        this.investmentService = investmentService;
    }

    @PostMapping
    public ResponseEntity<InvestmentDTO> addInvestment(@Valid @RequestBody InvestmentDTO request) {
        return ResponseEntity.status(201).body(ApiDtoMapper.toInvestmentResponse(
                investmentService.addInvestment(ApiDtoMapper.toInvestment(request))));
    }

    @GetMapping
    public ResponseEntity<List<InvestmentDTO>> getAllInvestments() {
        return ResponseEntity.ok(investmentService.getAllInvestments().stream()
                .map(ApiDtoMapper::toInvestmentResponse).toList());
    }

    @GetMapping("/{investmentId}")
    public ResponseEntity<InvestmentDTO> getInvestmentById(@PathVariable Long investmentId) {
        return ResponseEntity.ok(ApiDtoMapper.toInvestmentResponse(
                investmentService.getInvestmentById(investmentId)));
    }

    @PutMapping("/{investmentId}")
    public ResponseEntity<InvestmentDTO> updateInvestment(
            @PathVariable Long investmentId, @Valid @RequestBody InvestmentDTO request) {
        return ResponseEntity.ok(ApiDtoMapper.toInvestmentResponse(
                investmentService.updateInvestment(investmentId, ApiDtoMapper.toInvestment(request))));
    }

    @DeleteMapping("/{investmentId}")
    public ResponseEntity<Map<String, String>> deleteInvestment(@PathVariable Long investmentId) {
        investmentService.deleteInvestment(investmentId);
        return ResponseEntity.ok(Map.of("message", "Investment deleted successfully"));
    }

    @GetMapping("/search")
    public ResponseEntity<List<InvestmentDTO>> searchInvestments(@RequestParam String keyword) {
        return ResponseEntity.ok(investmentService.searchInvestments(keyword).stream()
                .map(ApiDtoMapper::toInvestmentResponse).toList());
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<InvestmentDTO>> getByType(@PathVariable String type) {
        return ResponseEntity.ok(investmentService.getInvestmentsByType(type).stream()
                .map(ApiDtoMapper::toInvestmentResponse).toList());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<InvestmentDTO>> getByStatus(@PathVariable String status) {
        return ResponseEntity.ok(investmentService.getInvestmentsByStatus(status).stream()
                .map(ApiDtoMapper::toInvestmentResponse).toList());
    }
}
