package com.examly.springapp.dto;

import java.util.List;

/**
 * AI agent diagnostics report. Every number and verdict is computed from real backend
 * checks against the live database and the live Groq API - nothing is simulated.
 *
 * @param backend connectivity and data-store check for this Spring Boot application
 * @param groq    live Groq API connectivity probe (one small real request)
 * @param tests   the natural-language test suite (A-H), each executed through the same
 *                {@code /api/ai/search/detailed} pipeline used by the UI
 */
public record AiDiagnosticsDTO(
        String generatedAt,
        BackendCheck backend,
        GroqCheck groq,
        List<TestCaseResult> tests) {

    /** Backend connectivity / retrieval check. */
    public record BackendCheck(boolean ok, String detail, long investmentRecords) {
    }

    /** Groq connectivity probe. Never exposes the API key. */
    public record GroqCheck(boolean configured, String model, boolean up, String status,
                            String errorCategory, int httpStatus, long latencyMs,
                            String detail) {
    }

    /** One executed natural-language test case. */
    public record TestCaseResult(String id, String title, String query, String expectation,
                                 boolean ok, String verdict, String engine, String engineDetail,
                                 List<String> criteria, int evaluated, int matched, int excluded,
                                 long latencyMs, List<TopResult> topResults) {
    }

    /** The leading genuine matches of one test case, drawn from stored records only. */
    public record TopResult(String name, String symbol, String type, String currency,
                            Double currentPrice, int matchPercentage, int dataCoverage,
                            String why) {
    }
}
