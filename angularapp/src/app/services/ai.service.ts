import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Investment } from '../models/investment.model';
import { AiSearchResponse } from '../models/ai-search.model';
import { AiAgentHealth, AiDiagnosticsReport } from '../models/ai-diagnostics.model';
import { AuthService } from './auth.service';
import { environment } from '../environment';

@Injectable({
  providedIn: 'root'
})
export class AiService {

  public apiUrl = environment.apiUrl;

  constructor(private http: HttpClient, private authService: AuthService) {}

  private getHeaders(): HttpHeaders {
    return new HttpHeaders({
      'Authorization': `Bearer ${this.authService.getToken()}`,
      'Content-Type': 'application/json'
    });
  }

  /** Plain (non-ranked) AI search kept for compatibility. */
  searchInvestments(query: string): Observable<Investment[]> {
    return this.http.post<Investment[]>(`${this.apiUrl}/api/ai/search`,
      { query },
      { headers: this.getHeaders() });
  }

  /**
   * AI search with backend-calculated match percentages, per-criterion explanations,
   * the configured threshold and the overall analysis panel content.
   * The raw, unmodified query is sent; the backend does all scoring.
   */
  searchDetailed(query: string): Observable<AiSearchResponse> {
    return this.http.post<AiSearchResponse>(`${this.apiUrl}/api/ai/search/detailed`,
      { query },
      { headers: this.getHeaders() });
  }

  /**
   * Live Gemini connectivity probe (administrators only). Sends one small real request
   * to the configured model and returns its status, latency and a sanitized error
   * category. The API key is never exposed.
   */
  health(): Observable<AiAgentHealth> {
    return this.http.get<AiAgentHealth>(`${this.apiUrl}/api/ai/health`,
      { headers: this.getHeaders() });
  }

  /**
   * Runs the full AI agent test suite (A-H) against the live backend through the same
   * pipeline the UI uses. Administrators only. Every result is computed from real checks.
   */
  runDiagnostics(): Observable<AiDiagnosticsReport> {
    return this.http.post<AiDiagnosticsReport>(`${this.apiUrl}/api/ai/diagnostics`, {},
      { headers: this.getHeaders() });
  }
}
