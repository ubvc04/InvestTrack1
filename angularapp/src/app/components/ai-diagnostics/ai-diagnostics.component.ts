import { Component, OnInit } from '@angular/core';
import { AiService } from 'src/app/services/ai.service';
import { AiAgentHealth, AiDiagnosticsReport } from 'src/app/models/ai-diagnostics.model';

/**
 * AI Agent Diagnostics (administrators only).
 *
 * Executes real checks against the live backend: Gemini connectivity (one small live
 * model request), investment database retrieval, natural-language parsing, mandatory
 * filtering, matching/ranking and result validation — each natural-language case (A-H)
 * runs through the exact same /api/ai/search/detailed pipeline the UI uses. Nothing on
 * this page is simulated and no verdict is hardcoded; a failed run keeps the last good
 * report on screen.
 */
@Component({
  selector: 'app-ai-diagnostics',
  templateUrl: './ai-diagnostics.component.html',
  styleUrls: ['./ai-diagnostics.component.css']
})
export class AiDiagnosticsComponent implements OnInit {

  health: AiAgentHealth | null = null;
  healthLoading = false;
  healthError = '';

  report: AiDiagnosticsReport | null = null;
  running = false;
  runError = '';

  constructor(private aiService: AiService) { }

  ngOnInit(): void {
    this.checkHealth();
  }

  checkHealth(): void {
    this.healthLoading = true;
    this.healthError = '';
    this.aiService.health().subscribe({
      next: (health) => {
        this.health = health;
        this.healthLoading = false;
      },
      error: (error) => {
        this.healthError = this.extractError(error);
        this.healthLoading = false;
      }
    });
  }

  runAiTests(): void {
    this.running = true;
    this.runError = '';
    this.aiService.runDiagnostics().subscribe({
      next: (report) => {
        this.report = report;
        this.health = report.gemini;
        this.running = false;
      },
      error: (error) => {
        // Keep the last good report visible when a refresh fails.
        this.runError = this.extractError(error);
        this.running = false;
      }
    });
  }

  passedCount(): number {
    return this.report ? this.report.tests.filter(test => test.ok).length : 0;
  }

  /** Freshest Gemini health: the suite result when present, otherwise the probe. */
  activeHealth(): AiAgentHealth | null {
    return this.report ? this.report.gemini : this.health;
  }

  engineLabel(engine: string | null): string {
    return engine === 'gemini-criteria' ? 'Gemini' :
      engine === 'deterministic-fallback' ? 'Deterministic fallback' : '—';
  }

  private extractError(error: any): string {
    const body = error?.error;
    if (typeof body === 'string' && body.trim()) {
      return body.trim();
    }
    if (typeof body?.message === 'string' && body.message.trim()) {
      return body.message.trim();
    }
    if (error?.status === 403) {
      return 'Administrator access is required to run AI diagnostics.';
    }
    if (error?.status === 0) {
      return 'Cannot reach the backend. Check that the server is running.';
    }
    return 'The request failed. Please try again.';
  }
}
