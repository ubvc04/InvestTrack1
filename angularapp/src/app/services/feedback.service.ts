import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Feedback } from '../models/feedback.model';
import { AuthService } from './auth.service';
import { environment } from '../environment';

@Injectable({
  providedIn: 'root'
})
export class FeedbackService{
  public apiUrl = environment.apiUrl;

constructor(private http: HttpClient, private authService: AuthService) {}


private getHeaders(): HttpHeaders {
  return new HttpHeaders({
    'Authorization': `Bearer ${this.authService.getToken()}`,
    'Content-Type': 'application/json'
  });
}


sendFeedback(feedback: Feedback): Observable<Feedback> {
  return this.http.post<Feedback>(`${this.apiUrl}/api/feedback`, feedback,
    { headers: this.getHeaders() });
}

getFeedbacks(): Observable<Feedback[]> {
  return this.http.get<Feedback[]>(`${this.apiUrl}/api/feedback`,
    { headers: this.getHeaders() });
}

getFeedbackById(feedbackId: number): Observable<Feedback> {
  return this.http.get<Feedback>(`${this.apiUrl}/api/feedback/${feedbackId}`,
    { headers: this.getHeaders() });
}

getAllFeedbacksByUserId(userId: number): Observable<Feedback[]> {
  return this.http.get<Feedback[]>(`${this.apiUrl}/api/feedback/user/${userId}`,
    { headers: this.getHeaders() });
}

deleteFeedback(feedbackId: number): Observable<any> {
  return this.http.delete(`${this.apiUrl}/api/feedback/${feedbackId}`,
    { headers: this.getHeaders() });
}

/**
 * Sends an administrator response to a feedback entry (Admin / SuperAdmin only).
 * The backend notifies the feedback owner and stores the response + response date.
 */
respondToFeedback(feedbackId: number, adminResponse: string): Observable<Feedback> {
  return this.http.put<Feedback>(
    `${this.apiUrl}/api/feedback/${feedbackId}/respond`,
    { adminResponse },
    { headers: this.getHeaders() });
}
}