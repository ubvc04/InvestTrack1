import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DashboardStats } from '../models/dashboard-stats.model';
import { AuthService } from './auth.service';
import { environment } from '../environment';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {

  public apiUrl = environment.apiUrl;

  constructor(private http: HttpClient, private authService: AuthService) {}

  private getHeaders(): HttpHeaders {
    return new HttpHeaders({
      'Authorization': `Bearer ${this.authService.getToken()}`,
      'Content-Type': 'application/json'
    });
  }

  /**
   * Aggregated Admin Console statistics (Admin / SuperAdmin only). The backend
   * computes every counter and chart dataset from persisted database records,
   * so KPI cards, charts and tables always agree.
   */
  getDashboardStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.apiUrl}/api/admin/dashboard`,
      { headers: this.getHeaders() });
  }
}
