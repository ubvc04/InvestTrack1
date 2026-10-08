import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Investment } from '../models/investment.model';
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

  searchInvestments(query: string): Observable<Investment[]> {
    return this.http.post<Investment[]>(`${this.apiUrl}/api/ai/search`,
      { query },
      { headers: this.getHeaders() });
  }
}
