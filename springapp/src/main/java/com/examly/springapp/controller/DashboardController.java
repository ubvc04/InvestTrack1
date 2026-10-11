package com.examly.springapp.controller;

import com.examly.springapp.dto.DashboardStatsDTO;
import com.examly.springapp.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aggregated statistics for the Admin Console.
 *
 * Access is restricted to the Admin and SuperAdmin roles by SecurityConfig
 * (/api/admin/**). Every number and chart dataset is computed server-side from
 * persisted records, so the KPI cards, charts and tables always agree.
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "http://localhost:8081")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }
}
