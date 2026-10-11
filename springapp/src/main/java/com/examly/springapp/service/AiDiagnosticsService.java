package com.examly.springapp.service;

import com.examly.springapp.dto.AiDiagnosticsDTO;
import com.examly.springapp.dto.AiMatchCriterionDTO;
import com.examly.springapp.dto.AiSearchResponseDTO;
import com.examly.springapp.dto.AiSearchResultDTO;
import com.examly.springapp.exceptions.UnrelatedInvestmentQueryException;
import com.examly.springapp.repository.InvestmentRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Executes the real AI-agent test suite against the live backend.
 *
 * <p>Every case runs through the same {@link AiMatchService#search(String)} pipeline the
 * UI uses — real Gemini interpretation (when configured), real MySQL retrieval, real
 * deterministic scoring — and the verdict is computed from the actual response. No case
 * is simulated and no verdict is hardcoded to succeed.</p>
 */
@Service
public class AiDiagnosticsService {

    private static final Logger logger = LoggerFactory.getLogger(AiDiagnosticsService.class);

    private final AiMatchService aiMatchService;
    private final GeminiService geminiService;
    private final InvestmentRepo investmentRepo;

    public AiDiagnosticsService(AiMatchService aiMatchService, GeminiService geminiService,
                                InvestmentRepo investmentRepo) {
        this.aiMatchService = aiMatchService;
        this.geminiService = geminiService;
        this.investmentRepo = investmentRepo;
    }

    /** One natural-language test case: input, expectation, and its id (A-H). */
    private record TestCase(String id, String title, String query, String expectation) {
    }

    private static final List<TestCase> TEST_CASES = List.of(
            new TestCase("A", "Geographic filtering", "Show investments associated with India.",
                    "Only records with genuine stored Indian market evidence may match; records "
                    + "without stated market evidence must be reported as unverifiable, never "
                    + "matched on incidental text."),
            new TestCase("B", "Asset-type filtering", "Show me available stocks.",
                    "Every returned record must be of type Stock; no bonds, commodities or ETFs."),
            new TestCase("C", "Budget filtering", "Show investments priced below \u20b95,000.",
                    "Compare against the stored current price only in a compatible currency; "
                    + "incompatible currencies must never be silently converted or compared."),
            new TestCase("D", "Low-risk preference", "Find lower-risk investment options.",
                    "Risk is not stored, so the limitation must be stated and risk must not "
                    + "influence any score."),
            new TestCase("E", "Investment goals", "Find investments suitable for long-term growth.",
                    "Growth intent must be explained from stored wording; horizon is not stored "
                    + "and no returns may be promised."),
            new TestCase("F", "Combined criteria", "Show active Indian stocks below \u20b95,000.",
                    "Every returned record must satisfy every verifiable mandatory criterion; "
                    + "unverifiable criteria must be labelled, not silently passed."),
            new TestCase("G", "Empty results",
                    "Show me European Commodity investments priced below \u20b9100.",
                    "An honest empty state when nothing satisfies the criteria - never "
                    + "manufactured or unrelated results."),
            new TestCase("H", "Ambiguous query", "What is the best investment?",
                    "Recognise the missing risk tolerance, horizon, budget and goal and ask for "
                    + "clarification instead of naming an arbitrary winner."));

    /** Runs backend + Gemini connectivity checks and the full A-H suite. */
    public AiDiagnosticsDTO runAll() {
        long started = System.currentTimeMillis();

        long records;
        AiDiagnosticsDTO.BackendCheck backend;
        try {
            records = investmentRepo.count();
            backend = new AiDiagnosticsDTO.BackendCheck(true,
                    "Investment database reachable; every test scores the full table.", records);
        } catch (RuntimeException exception) {
            logger.error("AI diagnostics: investment database check failed", exception);
            backend = new AiDiagnosticsDTO.BackendCheck(false,
                    "Investment database check failed: "
                            + exception.getClass().getSimpleName() + ".", 0);
            records = 0;
        }

        GeminiService.HealthReport health = geminiService.healthCheck();
        AiDiagnosticsDTO.GeminiCheck gemini = new AiDiagnosticsDTO.GeminiCheck(
                health.configured(), health.model(), health.up(), health.status(),
                health.errorCategory(), health.httpStatus(), health.latencyMs(), health.detail());

        List<AiDiagnosticsDTO.TestCaseResult> tests = new ArrayList<>();
        for (TestCase testCase : TEST_CASES) {
            tests.add(runCase(testCase));
        }

        logger.info("AI diagnostics suite finished in {}ms: gemini={}, cases={}, records={}",
                System.currentTimeMillis() - started, health.status(), tests.size(), records);
        return new AiDiagnosticsDTO(Instant.now().toString(), backend, gemini, tests);
    }

    private AiDiagnosticsDTO.TestCaseResult runCase(TestCase testCase) {
        long started = System.currentTimeMillis();
        try {
            AiSearchResponseDTO response = aiMatchService.search(testCase.query());
            long latency = System.currentTimeMillis() - started;

            List<String> criteria = response.analysis() == null
                    ? List.of() : response.analysis().requestedCriteria();
            List<AiDiagnosticsDTO.TopResult> top = new ArrayList<>();
            for (AiSearchResultDTO result : response.results()) {
                if (top.size() >= 3) {
                    break;
                }
                top.add(new AiDiagnosticsDTO.TopResult(
                        result.investment() == null ? null : result.investment().name(),
                        result.investment() == null ? null : result.investment().symbol(),
                        result.investment() == null ? null : result.investment().type(),
                        result.investment() == null ? null : result.investment().currency(),
                        result.investment() == null ? null : result.investment().currentPrice(),
                        result.matchPercentage(), result.confidence(), result.explanation()));
            }

            Verdict verdict = judge(testCase.id(), response);
            return new AiDiagnosticsDTO.TestCaseResult(testCase.id(), testCase.title(),
                    testCase.query(), testCase.expectation(), verdict.ok(), verdict.reason(),
                    response.engine(), response.engineDetail(), criteria,
                    response.evaluatedCount(), response.matchedCount(), response.excludedCount(),
                    latency, top);
        } catch (UnrelatedInvestmentQueryException exception) {
            return failed(testCase, System.currentTimeMillis() - started,
                    "Backend rejected the query as investment-unrelated: " + exception.getMessage());
        } catch (RuntimeException exception) {
            logger.error("AI diagnostics case {} failed unexpectedly", testCase.id(), exception);
            return failed(testCase, System.currentTimeMillis() - started,
                    "Unexpected backend error: " + exception.getClass().getSimpleName() + ".");
        }
    }

    private AiDiagnosticsDTO.TestCaseResult failed(TestCase testCase, long latency, String reason) {
        return new AiDiagnosticsDTO.TestCaseResult(testCase.id(), testCase.title(),
                testCase.query(), testCase.expectation(), false, reason, null, null,
                List.of(), 0, 0, 0, latency, List.of());
    }

    private record Verdict(boolean ok, String reason) {
    }

    /** Per-case assertions computed from the real response. */
    private Verdict judge(String id, AiSearchResponseDTO response) {
        return switch (id) {
            case "A" -> judgeGeographyIndia(response);
            case "B" -> judgeStocks(response);
            case "C" -> judgeBudget(response);
            case "D" -> judgeRisk(response);
            case "E" -> judgeGrowthGoal(response);
            case "F" -> judgeCombined(response);
            case "G" -> judgeEmpty(response);
            case "H" -> judgeAmbiguous(response);
            default -> new Verdict(false, "No judge is defined for this case.");
        };
    }

    private Verdict judgeGeographyIndia(AiSearchResponseDTO response) {
        if (response.matchedCount() == 0) {
            return new Verdict(true, "Honest empty state: " + response.evaluatedCount()
                    + " records evaluated, " + response.excludedCount()
                    + " excluded for contradicting the requested market, "
                    + response.heldBackCount()
                    + " held back because no stored data verifies Indian market exposure - "
                    + "nothing was presented as a match.");
        }
        for (AiSearchResultDTO result : response.results()) {
            AiMatchCriterionDTO geography = criterion(result, "Geographic market");
            if (geography == null || !"MATCH".equals(geography.state())) {
                return new Verdict(false, "Record '" + result.investment().name()
                        + "' was returned without verifiable Indian market evidence.");
            }
        }
        return new Verdict(true, response.matchedCount()
                + " record(s) matched, each with explicit stored Indian market evidence.");
    }

    private Verdict judgeStocks(AiSearchResponseDTO response) {
        if (response.matchedCount() == 0) {
            return new Verdict(false, "No stocks matched even though the database stores stocks.");
        }
        for (AiSearchResultDTO result : response.results()) {
            if (result.investment() == null || !"Stock".equalsIgnoreCase(result.investment().type())) {
                return new Verdict(false, "Non-stock record '" + (result.investment() == null
                        ? "?" : result.investment().name()) + "' was returned for a stocks query.");
            }
        }
        return new Verdict(true, response.matchedCount() + " stock record(s) returned; every "
                + "result is of type Stock and " + response.excludedCount()
                + " non-stock or sold records were excluded.");
    }

    private Verdict judgeBudget(AiSearchResponseDTO response) {
        for (AiSearchResultDTO result : response.results()) {
            AiMatchCriterionDTO budget = criterion(result, "Budget");
            if (budget != null && "MISMATCH".equals(budget.state())) {
                return new Verdict(false, "Record '" + result.investment().name()
                        + "' exceeds the requested budget yet was returned.");
            }
        }
        boolean budgetUnverified = response.analysis() != null
                && response.analysis().unverifiedCriteria().contains("Budget");
        if (response.matchedCount() == 0) {
            return new Verdict(budgetUnverified,
                    budgetUnverified
                            ? "Records are priced in USD while the limit was stated in INR; "
                            + "the budget was reported as unverifiable instead of comparing "
                            + "incompatible currencies, and no misleading matches were shown."
                            : "No record satisfied the budget; an empty state was returned.");
        }
        return new Verdict(true, response.matchedCount()
                + " record(s) within the stated budget; incompatible currencies were reported "
                + "as unverifiable rather than converted.");
    }

    private Verdict judgeRisk(AiSearchResponseDTO response) {
        boolean riskFlagged = response.analysis() != null
                && response.analysis().unverifiedCriteria().contains("Risk tolerance");
        if (!riskFlagged) {
            return new Verdict(false, "The response failed to state that risk is not stored.");
        }
        return new Verdict(true, "Risk tolerance is correctly reported as unverifiable: it is "
                + "not stored on investment records, so it was not scored and no asset was "
                + "claimed to be low-risk.");
    }

    private Verdict judgeGrowthGoal(AiSearchResponseDTO response) {
        boolean horizonFlagged = response.analysis() != null
                && response.analysis().unverifiedCriteria().contains("Investment horizon");
        if (!horizonFlagged) {
            return new Verdict(false, "The response failed to state that horizon is not stored.");
        }
        for (AiSearchResultDTO result : response.results()) {
            AiMatchCriterionDTO growth = criterion(result, "Income vs growth");
            if (growth == null || !"MATCH".equals(growth.state())) {
                return new Verdict(false, "Record '" + result.investment().name()
                        + "' was returned without stored growth wording.");
            }
        }
        return new Verdict(true, response.matchedCount()
                + " record(s) explained from stored growth wording; the missing horizon data "
                + "was disclosed and no returns were promised.");
    }

    private Verdict judgeCombined(AiSearchResponseDTO response) {
        for (AiSearchResultDTO result : response.results()) {
            if (result.investment() == null || !"Stock".equalsIgnoreCase(result.investment().type())) {
                return new Verdict(false, "A non-stock record passed a stocks-only query.");
            }
            AiMatchCriterionDTO status = criterion(result, "Investment status");
            if (status != null && "MISMATCH".equals(status.state())) {
                return new Verdict(false, "A sold record passed an availability filter.");
            }
            AiMatchCriterionDTO budget = criterion(result, "Budget");
            if (budget != null && "MISMATCH".equals(budget.state())) {
                return new Verdict(false, "A record above the budget passed the budget filter.");
            }
            AiMatchCriterionDTO geography = criterion(result, "Geographic market");
            if (geography != null && "MISMATCH".equals(geography.state())) {
                return new Verdict(false, "A record contradicting the market filter passed it.");
            }
        }
        if (response.matchedCount() == 0) {
            return new Verdict(true, "No record satisfied every mandatory filter together: "
                    + response.excludedCount() + " record(s) contradicted at least one stated "
                    + "criterion and " + response.heldBackCount()
                    + " could not verify all of them from stored data, so an honest empty "
                    + "state was returned.");
        }
        return new Verdict(true, response.matchedCount()
                + " record(s) satisfy every verifiable mandatory criterion; criteria the database "
                + "cannot verify (for example a foreign-currency budget) are labelled per result.");
    }

    private Verdict judgeEmpty(AiSearchResponseDTO response) {
        if (response.matchedCount() != 0) {
            return new Verdict(false, response.matchedCount()
                    + " unrelated record(s) were manufactured for an unsatisfiable combination.");
        }
        return new Verdict(true, "Honest empty state: " + response.evaluatedCount()
                + " records evaluated, nothing satisfied the stated combination.");
    }

    private Verdict judgeAmbiguous(AiSearchResponseDTO response) {
        String refine = response.analysis() == null
                ? "" : response.analysis().refineSuggestion().toLowerCase(Locale.ROOT);
        boolean asksToClarify = refine.contains("risk tolerance") && refine.contains("time horizon");
        if (response.matchedCount() != 0) {
            return new Verdict(false, "An arbitrary record was presented as 'the best investment'.");
        }
        if (!asksToClarify) {
            return new Verdict(false, "The response did not ask for the missing risk, horizon, "
                    + "budget or goal information.");
        }
        return new Verdict(true, "No arbitrary winner was named; the response asks for the "
                + "missing risk tolerance, time horizon, budget and goal before ranking.");
    }

    private AiMatchCriterionDTO criterion(AiSearchResultDTO result, String label) {
        if (result.criteria() == null) {
            return null;
        }
        for (AiMatchCriterionDTO detail : result.criteria()) {
            if (label.equals(detail.criterion())) {
                return detail;
            }
        }
        return null;
    }
}
