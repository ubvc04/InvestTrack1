import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Investment } from '../models/investment.model';
import { AuthService } from './auth.service';
import { environment } from '../environment';

@Injectable({
  providedIn: 'root'
})
export class InvestmentService {

  public apiUrl = environment.apiUrl;


  constructor(private http: HttpClient, private authService: AuthService) {}

  private getHeaders(): HttpHeaders {
    return new HttpHeaders({
      'Authorization': `Bearer ${this.authService.getToken()}`,
      'Content-Type': 'application/json'
    });
  }

  getAllInvestments(): Observable<Investment[]> {
    return this.http.get<Investment[]>(`${this.apiUrl}/api/investments`,
      { headers: this.getHeaders() });
  }

  getInvestmentById(investmentId: number): Observable<Investment> {
    return this.http.get<Investment>(`${this.apiUrl}/api/investments/${investmentId}`,
      { headers: this.getHeaders() });
  }

  addInvestment(investment: Investment): Observable<Investment> {
    return this.http.post<Investment>(`${this.apiUrl}/api/investments`, investment,
      { headers: this.getHeaders() });
  }

  updateInvestment(investmentId: number, investment: Investment): Observable<Investment> {
    return this.http.put<Investment>(`${this.apiUrl}/api/investments/${investmentId}`, investment,
      { headers: this.getHeaders() });
  }

  deleteInvestment(investmentId: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/api/investments/${investmentId}`,
      { headers: this.getHeaders() });
  }
}
