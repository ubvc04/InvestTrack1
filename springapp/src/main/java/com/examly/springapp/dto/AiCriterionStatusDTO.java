package com.examly.springapp.dto;

/**
 * Truthful per-criterion status used by the analysis panel.
 *
 * <p>Unlike the old pair of {@code requestedCriteria}/{@code unverifiedCriteria} string
 * lists (which rendered a blanket green check for every requested criterion and therefore
 * contradicted the "not stored" chips), this carries the real evaluation state of each
 * criterion so the UI can never show a criterion as verified when it is not.</p>
 *
 * @param label human readable criterion label, e.g. "Instrument type"
 * @param state {@code VERIFIED} when the database can evaluate this criterion for the
 *              query in general, {@code UNVERIFIABLE} when no stored field can decide it
 *              (for example risk tolerance or forward-looking return potential)
 * @param role  {@code MANDATORY} (a hard filter that can exclude a record) or
 *              {@code PREFERENCE} (influences ranking but never excludes)
 */
public record AiCriterionStatusDTO(
        String label,
        String state,
        String role) {
}
