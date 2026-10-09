package com.examly.springapp.dto;

/**
 * One criterion the AI matching engine compared for a single investment.
 *
 * <p>{@code state} is one of:</p>
 * <ul>
 *   <li>{@code MATCH} – the stored investment data satisfies the requested criterion</li>
 *   <li>{@code MISMATCH} – the stored investment data contradicts the requested criterion</li>
 *   <li>{@code UNVERIFIABLE} – the database does not hold enough information to decide
 *       (for example risk tolerance). Unverifiable criteria never lower the match
 *       percentage; they only reduce the reported {@code confidence}.</li>
 * </ul>
 *
 * <p>{@code weight} is the criterion weight after normalization to a 0–100 scale.</p>
 */
public record AiMatchCriterionDTO(
        String criterion,
        String state,
        double weight,
        String evidence) {
}
