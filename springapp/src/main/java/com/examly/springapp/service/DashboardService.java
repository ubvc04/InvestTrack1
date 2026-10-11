package com.examly.springapp.service;

import com.examly.springapp.dto.DashboardStatsDTO;
import com.examly.springapp.dto.DashboardStatsDTO.FeedbackRow;
import com.examly.springapp.dto.DashboardStatsDTO.FeedbackStats;
import com.examly.springapp.dto.DashboardStatsDTO.InvestmentRow;
import com.examly.springapp.dto.DashboardStatsDTO.InvestmentStats;
import com.examly.springapp.dto.DashboardStatsDTO.InquiryRow;
import com.examly.springapp.dto.DashboardStatsDTO.InquiryStats;
import com.examly.springapp.dto.DashboardStatsDTO.PeriodCount;
import com.examly.springapp.model.Feedback;
import com.examly.springapp.model.Investment;
import com.examly.springapp.model.InvestmentInquiry;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.InvestmentInquiryRepo;
import com.examly.springapp.repository.InvestmentRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the Admin Console statistics in a single read-only transaction.
 *
 * All counters and chart datasets come from database GROUP BY queries or simple
 * count() calls over the existing repositories - no numbers are hardcoded and
 * no full record lists are downloaded just to compute summaries. The "recent"
 * lists are bounded (top 5 / top 3) database queries.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    /** Status used for inquiry rows that store no status (mirrors the UI default). */
    public static final String DEFAULT_INQUIRY_STATUS = "PENDING";

    private final InvestmentRepo investmentRepo;
    private final InvestmentInquiryRepo inquiryRepo;
    private final FeedbackRepo feedbackRepo;

    public DashboardService(InvestmentRepo investmentRepo,
                            InvestmentInquiryRepo inquiryRepo,
                            FeedbackRepo feedbackRepo) {
        this.investmentRepo = investmentRepo;
        this.inquiryRepo = inquiryRepo;
        this.feedbackRepo = feedbackRepo;
    }

    public DashboardStatsDTO getStats() {
        InvestmentStats investments = new InvestmentStats(
                investmentRepo.count(),
                investmentRepo.countByStatusIgnoreCase("Active"),
                toCountMap(investmentRepo.countInvestmentsByType(), "Other"),
                toPeriodCounts(investmentRepo.countInvestmentsByMonth()));

        InquiryStats inquiries = new InquiryStats(
                inquiryRepo.count(),
                toCountMap(inquiryRepo.countInquiriesByStatus(), DEFAULT_INQUIRY_STATUS),
                toCountMap(inquiryRepo.countInquiriesByPriority(), "Unprioritized"),
                toPeriodCounts(inquiryRepo.countInquiriesByDay()));

        FeedbackStats feedbacks = new FeedbackStats(
                feedbackRepo.count(),
                toCountMap(feedbackRepo.countFeedbackByCategory(), "Uncategorized"));

        List<InquiryRow> recentInquiries = inquiryRepo.findTop5ByOrderByInquiryDateDesc().stream()
                .map(DashboardService::toInquiryRow).toList();
        List<InvestmentRow> recentInvestments = investmentRepo.findTop5ByOrderByInvestmentIdDesc().stream()
                .map(DashboardService::toInvestmentRow).toList();
        List<FeedbackRow> recentFeedback = feedbackRepo.findTop3ByOrderByFeedbackIdDesc().stream()
                .map(DashboardService::toFeedbackRow).toList();

        return new DashboardStatsDTO(investments, inquiries, feedbacks,
                recentInquiries, recentInvestments, recentFeedback);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Converts [key, count] GROUP BY rows into a map ordered by descending count
     * (ties broken alphabetically so chart ordering is stable). A NULL key is
     * bucketed under the supplied fallback label.
     */
    private static Map<String, Long> toCountMap(List<Object[]> rows, String fallbackLabel) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String key = row[0] == null ? fallbackLabel : row[0].toString();
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            counts.merge(key, count, Long::sum);
        }
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    /** Converts [period, count] rows into a time-ascending list, skipping NULL buckets. */
    private static List<PeriodCount> toPeriodCounts(List<Object[]> rows) {
        List<PeriodCount> periods = new ArrayList<>();
        for (Object[] row : rows) {
            if (row[0] == null) continue;
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            periods.add(new PeriodCount(row[0].toString(), count));
        }
        periods.sort(Comparator.comparing(PeriodCount::period));
        return periods;
    }

    private static InquiryRow toInquiryRow(InvestmentInquiry inquiry) {
        return new InquiryRow(
                inquiry.getInvestment() != null ? inquiry.getInvestment().getName() : "-",
                inquiry.getUser() != null ? inquiry.getUser().getUsername() : "-",
                inquiry.getPriority(),
                inquiry.getStatus() == null ? DEFAULT_INQUIRY_STATUS : inquiry.getStatus(),
                inquiry.getInquiryDate() != null ? inquiry.getInquiryDate().toLocalDate().toString() : null);
    }

    /** Recorded catalogue values only - never live or invented market data. */
    private static InvestmentRow toInvestmentRow(Investment investment) {
        return new InvestmentRow(
                investment.getName(),
                investment.getType(),
                investment.getAssetClass(),
                investment.getMarket(),
                investment.getStatus(),
                investment.getCurrentPrice(),
                investment.getCurrency());
    }

    private static FeedbackRow toFeedbackRow(Feedback feedback) {
        return new FeedbackRow(
                feedback.getCategory(),
                feedback.getDate(),
                feedback.getFeedbackText(),
                feedback.getUser() != null ? feedback.getUser().getUsername() : null);
    }
}
