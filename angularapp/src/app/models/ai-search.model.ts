import { Investment } from './investment.model';

/** Outcome of one criterion for one investment. */
export type AiCriterionState = 'MATCH' | 'MISMATCH' | 'UNVERIFIABLE';

export interface AiMatchCriterion {
  criterion: string;
  state: AiCriterionState;
  weight: number;
  evidence: string;
}

export interface AiSearchResult {
  investment: Investment;
  /** Match percentage as a 0..1 fraction (matchPercentage / 100). */
  relevanceScore: number;
  matchedFactors: string[];
  explanation: string;
  /** Deterministic AI match percentage, 0..100. */
  matchPercentage: number;
  /** Share of the request that could be verified from stored data, 0..100. */
  confidence: number;
  criteria: AiMatchCriterion[];
  passedThreshold: boolean;
}

export interface AiAnalysis {
  objective: string;
  bestMatches: string;
  whyTheyMatch: string;
  thingsToConsider: string;
  refineSuggestion: string;
  requestedCriteria: string[];
  unverifiedCriteria: string[];
}

export interface AiSearchResponse {
  query: string;
  overview: string;
  /** true only when Gemini actually interpreted the query. */
  semanticRankingUsed: boolean;
  results: AiSearchResult[];
  analysis: AiAnalysis;
  threshold: number;
  evaluatedCount: number;
  matchedCount: number;
  /** "gemini-criteria" | "deterministic-fallback" */
  engine: string;
}
