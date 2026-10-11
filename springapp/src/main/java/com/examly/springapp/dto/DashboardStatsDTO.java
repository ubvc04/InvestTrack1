package com.examly.springapp.dto;

import java.util.List;
import java.util.Map;

/**
 * Aggregated statistics for the Admin Console.
 *
 * Every value is computed by the backend from persisted records - the frontend
 * never hardcodes, fabricates, or independently invents dashboard numbers.
 * Chart datasets and KPI cards therefore always agree with the database.
 */
public record DashboardStatsDTO(
        InvestmentStats investments,
        InquiryStats inquiries,
        FeedbackStats feedbacks,
        List<InquiryRow> recentInquiries,
        List<InvestmentRow> recentInvestments,
        List<FeedbackRow> recentFeedback) {

    /** Catalogue counters and per-asset-type distribution. */
    public record InvestmentStats(long total, long active, Map<String, Long> byType, List<PeriodCount> activity) {}

    /** Inquiry workflow counters by stored status and priority, plus the creation trend. */
    public record InquiryStats(long total, Map<String, Long> byStatus, Map<String, Long> byPriority, List<PeriodCount> trend) {}

    /** Feedback counters grouped by stored category. */
    public record FeedbackStats(long total, Map<String, Long> byCategory) {}

    /** A time bucket (yyyy-MM month or yyyy-MM-dd day) with its record count. */
    public record PeriodCount(String period, long count) {}

    /** Slim row for the Recent Inquiries table (latest five). */
    public record InquiryRow(String investment, String user, String priority, String status, String date) {}

    /** Slim row for the Investment Catalog snapshot (latest five). Prices are
     *  the values recorded in the catalogue - never live market data. */
    public record InvestmentRow(String name, String type, String assetClass, String market,
                                String status, Double currentPrice, String currency) {}

    /** Slim row for the Latest Customer Feedback section (latest three). */
    public record FeedbackRow(String category, String date, String text, String user) {}
}
