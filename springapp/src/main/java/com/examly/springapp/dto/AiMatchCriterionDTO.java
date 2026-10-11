package com.examly.springapp.dto;

/**
 * One criterion the AI matching engine compared for a single investment.
 *
 * <p>{@code state} is one of:</p>
 * <ul>
 *   <li>{@code MATCH} – the stored investment data satisfies the requested criterion
 *       (credit 1.0)</li>
 *   <li>{@code PARTIAL} – the stored evidence supports the criterion only partly
 *       (0 &lt; credit &lt; 1). Used by the historical price performance criterion, which is
 *       graded linearly from its documented bounds.</li>
 *   <li>{@code MISMATCH} – the stored investment data contradicts the requested criterion
 *       (credit 0)</li>
 *   <li>{@code UNVERIFIABLE} – the database does not hold enough information to decide
 *       (for example risk tolerance or forward-looking return potential). Unverifiable
 *       criteria stay in the match-percentage denominator (they never inflate the score)
 *       and lower the reported data coverage.</li>
 * </ul>
 *
 * <p>{@code weight} is the criterion weight after normalization to a 0–100 scale, and
 * {@code credit} is the share of that weight the stored evidence earned (0..1). The
 * criterion's contribution to the match percentage is {@code weight * credit}.</p>
 *
 * <p>{@code role} is {@code MANDATORY} for hard filters (instrument type, sector, budget,
 * geographic market, investment status) that can exclude a record, or {@code PREFERENCE}
 * for criteria that only influence ranking (risk, horizon, liquidity, volatility,
 * income-vs-growth, return objective, historical price performance).</p>
 */
public record AiMatchCriterionDTO(
        String criterion,
        String state,
        double weight,
        String evidence,
        String role,
        double credit) {
}
