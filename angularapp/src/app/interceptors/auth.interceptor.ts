import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
  HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  constructor(private authService: AuthService, private router: Router) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const token = this.authService.getToken();

    // Attach token to every request if present
    if (token) {
      request = request.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
    }

    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        // If unauthorized, clear session and redirect to login
        if (error.status === 401 && !request.url.endsWith('/api/logout')) {
          this.authService.logout();
          this.router.navigate(['/login']);
        }

        // The backend blocks every API for accounts flagged mustChangePassword
        // with this specific 403. Send the user to the change-password page
        // instead of letting each page fail into the generic error screen.
        if (error.status === 403) {
          const body =
            typeof error.error === 'string' ? error.error :
            error.error?.error ?? error.error?.message ?? '';
          if (typeof body === 'string' && body.toLowerCase().includes('change your password')) {
            const target = this.authService.getUserRole() === 'User'
              ? '/usernav/change-password'
              : '/adminnav/change-password';
            this.router.navigate([target]);
          }
        }

        return throwError(() => error);
      })
    );
  }
}