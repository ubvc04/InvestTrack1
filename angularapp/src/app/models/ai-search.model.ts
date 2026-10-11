import { Investment } from './investment.model';

/** Outcome of one criterion for one investment. */
export type AiCriterionState = 'MATCH' | 'PARTIAL' | 'MISMATCH' | 'UNVERIFIABLE';

/** Hard filter (can exclude a record) versus ranking-only preference. */
export type AiCriterionRole = 'MANDATORY' | 'PREFERENCE';

export interface AiMatchCriterion {
  criterion: string;
  state: AiCriterionState;
  weight: number;
  evidence: string;
  role: AiCriterionRole;
  /** Share of this criterion's weight the stored evidence earned, 0..1 (weight * credit = contribution). */
  credit: number;
}

export interface AiSearchResult {
  investment: Investment;
  /** Match percentage as a 0..1 fraction (matchPercentage / 100). */
  relevanceScore: number;
  matchedFactors: string[];
  explanation: string;
  /**
   * Confirmed-fit percentage, 0..100: (weight of criteria the backend confirmed satisfied)
   * / (total weight of all requested criteria) x 100. Criteria that stored data cannot
   * verify earn 0 but stay in the denominator, so a result never reaches 100% while a stated
   * criterion is unverified.
   */
  matchPercentage: number;
  /** Share of the request the stored data could evaluate at all, 0..100. Not a statistical confidence. */
  dataCoverage: number;
  /** FULL_MATCH only when every requested criterion was verified and satisfied; else PARTIAL_MATCH. */
  matchTier: 'FULL_MATCH' | 'PARTIAL_MATCH';
  /** Clearly-labelled historical context (e.g. price change since purchase). Never a forecast. */
  historicalPerformance?: string | null;
  criteria: AiMatchCriterion[];
  /** True when the confirmed-fit score reached the configured strong-match line. */
  passedThreshold: boolean;
}

/** Truthful per-criterion status rendered once in the analysis panel. */
export interface AiCriterionStatus {
  label: string;
  state: 'VERIFIED' | 'UNVERIFIABLE';
  role: AiCriterionRole;
}

export interface AiAnalysis {
  objective: string;
  bestMatches: string;
  whyTheyMatch: string;
  thingsToConsider: string;
  refineSuggestion: string;
  /** Single source for the criteria chips: label + real state + role. */
  criteriaStatus: AiCriterionStatus[];
  requestedCriteria: string[];
  unverifiedCriteria: string[];
}

export interface AiSearchResponse {
  query: string;
  overview: string;
  /** true only when Groq actually interpreted the query. */
  semanticRankingUsed: boolean;
  results: AiSearchResult[];
  analysis: AiAnalysis;
  threshold: number;
  evaluatedCount: number;
  /** Records that satisfied every mandatory criterion (size of results). */
  matchedCount: number;
  /** Results where every requested criterion was verified and satisfied (matchTier FULL_MATCH). */
  fullMatchCount?: number;
  /** "Groq-criteria" | "deterministic-fallback" */
  engine: string;
  /** Records that verifiably contradicted a mandatory criterion (excluded before ranking). */
  excludedCount?: number;
  /** Records held back because stored data could not verify the criteria needed to rank them. */
  heldBackCount?: number;
  /** Honest, human-readable data limitations for this query. */
  limitations?: string[];
  /** The documented scoring formula. */
  matchScoreDefinition?: string;
  /** Sanitized engine detail: model + latency, or the reason the fallback ran. */
  engineDetail?: string;
}
