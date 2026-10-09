import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { Notification, NotificationList } from '../models/notification.model';
import { AuthService } from './auth.service';
import { environment } from '../environment';

/**
 * In-app notification API. Every call is made with the caller's own JWT — the backend always
 * derives the recipient from the token, so no recipient id is ever sent from here.
 */
@Injectable({
  providedIn: 'root'
})
export class NotificationService {

  public apiUrl = environment.apiUrl;

  constructor(private http: HttpClient, private authService: AuthService) {}

  getNotifications(page = 0, size = 20): Observable<NotificationList> {
    const params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size));
    return this.http.get<NotificationList>(`${this.apiUrl}/api/notifications`, { params });
  }

  getUnreadCount(): Observable<number> {
    return this.http
      .get<{ count: number }>(`${this.apiUrl}/api/notifications/unread-count`)
      .pipe(map(response => response.count));
  }

  markAsRead(notificationId: number): Observable<Notification> {
    return this.http.put<Notification>(
      `${this.apiUrl}/api/notifications/${notificationId}/read`, {});
  }

  markAllAsRead(): Observable<{ message: string; updated: number; unreadCount: number }> {
    return this.http.put<{ message: string; updated: number; unreadCount: number }>(
      `${this.apiUrl}/api/notifications/read-all`, {});
  }
}
