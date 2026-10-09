import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Investment } from '../models/investment.model';
import { AiSearchResponse } from '../models/ai-search.model';
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
}
