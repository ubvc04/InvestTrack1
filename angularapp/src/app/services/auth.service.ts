import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { Login } from '../models/login.model';
import { User } from '../models/user.model';
import { LoginResponse } from '../models/login-response.model';
import { environment } from '../environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  public apiUrl = environment.apiUrl;


  private userRoleSubject = new BehaviorSubject<string>(localStorage.getItem('userRole') || '');
  private userIdSubject   = new BehaviorSubject<number>(Number(localStorage.getItem('userId')) || 0);
  private usernameSubject = new BehaviorSubject<string>(localStorage.getItem('username') || '');

  userRole$  = this.userRoleSubject.asObservable();
  userId$    = this.userIdSubject.asObservable();
  username$  = this.usernameSubject.asObservable();

  constructor(private http: HttpClient) {}

  register(user: User): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/api/register`, user);
  }

  sendOtp(email: string): Observable<any> {
    return this.http.post(
      `${this.apiUrl}/api/send-otp`,
      {
        email: email
      }
    );
  }
  
  verifyOtp(
    email: string,
    otp: string
  ): Observable<any> {
  
    return this.http.post(
      `${this.apiUrl}/api/verify-otp`,
      {
        email: email,
        otp: otp
      }
    );
  }

  login(login: Login): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/api/login`, login).pipe(
      tap(response => this.storeLoginResponse(response))
    );
  }

  sendForgotPasswordOtp(email: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/api/forgot-password/send-otp`, { email });
  }

  verifyForgotPasswordOtp(email: string, otp: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(
      `${this.apiUrl}/api/forgot-password/verify-otp`, { email, otp }
    ).pipe(tap(response => {
      this.storeLoginResponse(response);
      localStorage.setItem('passwordRecoverySession', 'true');
    }));
  }

  changeForgottenPassword(data: { newPassword: string; confirmPassword: string }): Observable<any> {
    return this.http.put(`${this.apiUrl}/api/forgot-password/change-password`, data, {
      headers: { Authorization: `Bearer ${this.getToken()}` }
    });
  }

  sendPhoneOtp(phoneNumber: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/api/phone/send-otp`, { phoneNumber });
  }

  verifyPhoneOtp(phoneNumber: string, otp: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/api/phone/verify-otp`, { phoneNumber, otp });
  }

  logout(): void {
    const token = this.getToken();
    if (token) {
      this.http.post(`${this.apiUrl}/api/logout`, {}, {
        headers: { Authorization: `Bearer ${token}` }
      }).subscribe({
        error: () => {
          // The local session is cleared below even when the server is unavailable.
        }
      });
    }
    localStorage.removeItem('token');
    localStorage.removeItem('userRole');
    localStorage.removeItem('userId');
    localStorage.removeItem('username');
    localStorage.removeItem('passwordRecoverySession');
    localStorage.removeItem('mustChangePassword');
    this.userRoleSubject.next('');
    this.userIdSubject.next(0);
    this.usernameSubject.next('');
  }

  isPasswordRecoverySession(): boolean {
    return localStorage.getItem('passwordRecoverySession') === 'true';
  }

  private storeLoginResponse(response: LoginResponse): void {
    localStorage.setItem('token', response.token);
    localStorage.setItem('userRole', response.userRole);
    localStorage.setItem('userId', response.userId.toString());
    localStorage.setItem('username', response.username);
    localStorage.setItem('mustChangePassword', String(response.mustChangePassword));
    this.userRoleSubject.next(response.userRole);
    this.userIdSubject.next(response.userId);
    this.usernameSubject.next(response.username);
  }

  mustChangePasswordRequired(): boolean {
    return localStorage.getItem('mustChangePassword') === 'true';
  }

  changePassword(data: any): Observable<any> {
    return this.http.put(
      `${this.apiUrl}/api/change-password`,
      data,
      {
        headers: {
          Authorization: `Bearer ${this.getToken()}`
        }
      }
    );
  }

  getToken(): string {
    return localStorage.getItem('token') || '';
  }

  getUserRole(): string {
    return localStorage.getItem('userRole') || '';
  }

  getUserId(): number {
    return Number(localStorage.getItem('userId')) || 0;
  }

  getUsername(): string {
    return localStorage.getItem('username') || '';
  }

  isLoggedIn(): boolean {
    return !!localStorage.getItem('token');
  }

  isAdmin(): boolean {
    return this.getUserRole() === 'Admin' || this.isSuperAdmin();
  }

  isUser(): boolean {
    return this.getUserRole() === 'User';
  }

  isSuperAdmin(): boolean {
    return this.getUserRole() === 'SuperAdmin';
  }

  createAdmin(user: User): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/api/admins`, user);
  }

  getAdmins(): Observable<User[]> {
    return this.http.get<User[]>(`${this.apiUrl}/api/admins`);
  }

}
