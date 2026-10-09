package com.examly.springapp.model;

import java.util.Locale;

/**
 * The investment statuses supported by the InvestTrack business model.
 *
 * <ul>
 *   <li>{@code Active} – the investment is currently held / available as an active investment.</li>
 *   <li>{@code Sold} – the investment has been sold or otherwise disposed of.</li>
 * </ul>
 *
 * <p>The application never used {@code Pending} as a real workflow state: the admin add/edit
 * screens only ever offered {@code Active} and {@code Sold}, the entity has no settlement,
 * transaction or maturity columns, and every legacy {@code Pending} row holds complete pricing,
 * quantity and date data. {@code Pending} is therefore an erroneous legacy value, while
 * {@code Matured} (paid out at maturity) and {@code Suspended} (trading halted but still held)
 * describe legacy states that map safely onto the supported model.</p>
 *
 * <p>New and updated records are validated so no other permanent status can be stored again.</p>
 */
public enum InvestmentStatus {

    ACTIVE("Active"),
    SOLD("Sold");

    private final String label;

    InvestmentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Returns the canonical label for a supported status (case-insensitive, trimmed),
     * or {@code null} when the value is not supported.
     */
    public static String canonicalize(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        for (InvestmentStatus status : values()) {
            if (status.label.equalsIgnoreCase(normalized)) {
                return status.label;
            }
        }
        return null;
    }

    public static boolean isSupported(String raw) {
        return canonicalize(raw) != null;
    }

    /**
     * Maps a legacy status value onto the supported model.
     *
     * <p>Documented migration rules:</p>
     * <ul>
     *   <li>{@code Pending} → {@code Active} only when the record is complete (positive prices,
     *       quantity of at least one, a valid purchase date and a description). The schema holds
     *       no settlement workflow, so a complete pending row is an ordinary held position. An
     *       incomplete row returns {@code null} so it is left untouched and reported.</li>
     *   <li>{@code Matured} → {@code Sold}: a matured instrument has paid out and the position
     *       no longer exists.</li>
     *   <li>{@code Suspended} → {@code Active}: a trading halt does not dispose of a holding.</li>
     *   <li>Anything else → {@code null}: never rewritten automatically.</li>
     * </ul>
     *
     * @param raw            legacy status value
     * @param recordComplete whether the row carries complete pricing/quantity/date data
     * @return the supported status label, or {@code null} when it must not be changed
     */
    public static String mapLegacy(String raw, boolean recordComplete) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "pending":
                return recordComplete ? ACTIVE.label : null;
            case "matured":
                return SOLD.label;
            case "suspended":
                return ACTIVE.label;
            default:
                return null;
        }
    }

    /** True when the row carries the data needed to classify it as a complete holding. */
    public static boolean isCompleteRecord(Double purchasePrice, Double currentPrice,
                                           Integer quantity, String purchaseDate, String description) {
        return purchasePrice != null && purchasePrice > 0
                && currentPrice != null && currentPrice > 0
                && quantity != null && quantity >= 1
                && purchaseDate != null && purchaseDate.matches("\\d{4}-\\d{2}-\\d{2}")
                && description != null && !description.isBlank();
    }
}
