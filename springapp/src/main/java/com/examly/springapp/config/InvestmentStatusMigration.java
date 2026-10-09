package com.examly.springapp.config;

import com.examly.springapp.model.Investment;
import com.examly.springapp.model.InvestmentStatus;
import com.examly.springapp.repository.InvestmentRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-time, repeatable migration that brings legacy investment statuses onto the supported
 * model ({@link InvestmentStatus}: {@code Active} / {@code Sold}).
 *
 * <p>It runs after the seed initializer (order 4) so rows inserted by seeding are covered too.
 * The migration is idempotent and conservative:</p>
 * <ul>
 *   <li>only rows whose status is not already {@code Active} or {@code Sold} are touched;</li>
 *   <li>{@code Pending} becomes {@code Active} only for complete records (positive prices,
 *       quantity, valid date, description) — incomplete rows are reported and left untouched;</li>
 *   <li>{@code Matured} becomes {@code Sold}, {@code Suspended} becomes {@code Active} (see
 *       {@link InvestmentStatus#mapLegacy});</li>
 *   <li>case/whitespace variants are canonicalized;</li>
 *   <li>any other value is never rewritten automatically — it is logged for review;</li>
 *   <li>ownership, prices, quantities, dates, seed keys and every other column stay identical,
 *       and no row is ever deleted.</li>
 * </ul>
 */
@Component
@Order(4)
public class InvestmentStatusMigration implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(InvestmentStatusMigration.class);

    private final InvestmentRepo investmentRepo;

    public InvestmentStatusMigration(InvestmentRepo investmentRepo) {
        this.investmentRepo = investmentRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            migrate();
        } catch (RuntimeException exception) {
            logger.error("Investment status migration failed; existing rows were left untouched",
                    exception);
        }
    }

    private void migrate() {
        List<Investment> investments = investmentRepo.findAll();
        List<Investment> changed = new ArrayList<>();
        Map<String, Integer> unresolved = new LinkedHashMap<>();
        int pendingIncomplete = 0;

        for (Investment investment : investments) {
            String current = investment.getStatus();
            String canonical = InvestmentStatus.canonicalize(current);
            if (canonical != null) {
                if (!canonical.equals(current)) {
                    investment.setStatus(canonical);
                    changed.add(investment);
                }
                continue;
            }

            boolean complete = InvestmentStatus.isCompleteRecord(
                    investment.getPurchasePrice(), investment.getCurrentPrice(),
                    investment.getQuantity(), investment.getPurchaseDate(),
                    investment.getDescription());
            String mapped = InvestmentStatus.mapLegacy(current, complete);
            if (mapped != null) {
                investment.setStatus(mapped);
                changed.add(investment);
                logger.info("Investment status migration: id={} '{}' -> '{}' (legacy value '{}')",
                        investment.getInvestmentId(), current, mapped, current);
            } else {
                if (current != null && current.trim().toLowerCase(java.util.Locale.ROOT).equals("pending")) {
                    pendingIncomplete++;
                }
                unresolved.merge(current == null ? "<null>" : current.trim(), 1, Integer::sum);
            }
        }

        if (!changed.isEmpty()) {
            for (int start = 0; start < changed.size(); start += 100) {
                investmentRepo.saveAll(changed.subList(start, Math.min(start + 100, changed.size())));
            }
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Investment investment : investments) {
            counts.merge(String.valueOf(investment.getStatus()), 1, Integer::sum);
        }

        logger.info("Investment status migration complete: evaluated={}, corrected={}, "
                        + "unchanged={}, left for review={}, status counts={}",
                investments.size(), changed.size(),
                investments.size() - changed.size() - sum(unresolved.values()),
                sum(unresolved.values()), counts);
        if (!unresolved.isEmpty()) {
            logger.warn("Investment status migration: values that were NOT rewritten: {}{}",
                    unresolved,
                    pendingIncomplete > 0
                            ? " (" + pendingIncomplete + " incomplete Pending row(s) left untouched)"
                            : "");
        }
    }

    private int sum(Iterable<Integer> values) {
        int total = 0;
        for (Integer value : values) {
            total += value;
        }
        return total;
    }
}
