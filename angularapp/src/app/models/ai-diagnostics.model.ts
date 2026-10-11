/** Live Gemini connectivity probe result (administrators only). */
export interface AiAgentHealth {
  configured: boolean;
  model: string;
  up: boolean;
  status: string;
  errorCategory: string;
  httpStatus: number;
  latencyMs: number;
  detail: string;
  checkedAtEpochMs: number;
}

/** One leading genuine match of a diagnostics test case. */
export interface AiDiagnosticsTopResult {
  name: string | null;
  symbol: string | null;
  type: string | null;
  currency: string | null;
  currentPrice: number | null;
  matchPercentage: number;
  confidence: number;
  why: string | null;
}

/** One executed natural-language test case (A-H). */
export interface AiDiagnosticsTestCase {
  id: string;
  title: string;
  query: string;
  expectation: string;
  ok: boolean;
  verdict: string;
  engine: string | null;
  engineDetail: string | null;
  criteria: string[];
  evaluated: number;
  matched: number;
  excluded: number;
  latencyMs: number;
  topResults: AiDiagnosticsTopResult[];
}

/** Full AI agent diagnostics report, computed from real backend checks. */
export interface AiDiagnosticsReport {
  generatedAt: string;
  backend: { ok: boolean; detail: string; investmentRecords: number };
  gemini: AiAgentHealth;
  tests: AiDiagnosticsTestCase[];
}
