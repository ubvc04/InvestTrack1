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
      tap((response: LoginResponse) => {
        localStorage.setItem('token', response.token);
        localStorage.setItem('userRole', response.userRole);
        localStorage.setItem('userId', response.userId.toString());
        localStorage.setItem('username', response.username);
        this.userRoleSubject.next(response.userRole);
        this.userIdSubject.next(response.userId);
        this.usernameSubject.next(response.username);
      })
    );
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
    this.userRoleSubject.next('');
    this.userIdSubject.next(0);
    this.usernameSubject.next('');
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
