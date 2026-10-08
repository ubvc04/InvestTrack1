import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { InvestmentInquiry } from '../models/investment-inquiry.model';
import { AuthService } from './auth.service';
import { environment } from '../environment';

@Injectable({
  providedIn: 'root'
})
export class InvestmentInquiryService {
  public apiUrl = environment.apiUrl;


  constructor(private http: HttpClient, private authService: AuthService) {}

  private getHeaders(): HttpHeaders {
    return new HttpHeaders({
      'Authorization': `Bearer ${this.authService.getToken()}`,
      'Content-Type': 'application/json'
    });
  }

  addInquiry(inquiry: InvestmentInquiry): Observable<InvestmentInquiry> {
    return this.http.post<InvestmentInquiry>(`${this.apiUrl}/api/inquiries`, inquiry,
      { headers: this.getHeaders() });
  }

  getAllInquiries(): Observable<InvestmentInquiry[]> {
    return this.http.get<InvestmentInquiry[]>(`${this.apiUrl}/api/inquiries`,
      { headers: this.getHeaders() });
  }

  getInquiryById(inquiryId: number): Observable<InvestmentInquiry> {
    return this.http.get<InvestmentInquiry>(`${this.apiUrl}/api/inquiries/${inquiryId}`,
      { headers: this.getHeaders() });
  }

  getInquiriesByUserId(userId: number): Observable<InvestmentInquiry[]> {
    return this.http.get<InvestmentInquiry[]>(`${this.apiUrl}/api/inquiries/user/${userId}`,
      { headers: this.getHeaders() });
  }

  updateInquiry(inquiryId: number, inquiry: InvestmentInquiry): Observable<InvestmentInquiry> {
    return this.http.put<InvestmentInquiry>(`${this.apiUrl}/api/inquiries/${inquiryId}`, inquiry,
      { headers: this.getHeaders() });
  }

  deleteInquiry(inquiryId: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/api/inquiries/${inquiryId}`,
      { headers: this.getHeaders() });
  }

}
