import { ComponentFixture, TestBed, fakeAsync, flush } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';

import { ForgotPasswordComponent } from './forgot-password.component';
import { AuthService } from 'src/app/services/auth.service';
import { Router } from '@angular/router';

describe('ForgotPasswordComponent', () => {
  let component: ForgotPasswordComponent;
  let fixture: ComponentFixture<ForgotPasswordComponent>;
  let authService: AuthService;
  let router: Router;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ForgotPasswordComponent],
      imports: [
        FormsModule,
        HttpClientTestingModule,
        RouterTestingModule.withRoutes([])
      ]
    });
    fixture = TestBed.createComponent(ForgotPasswordComponent);
    component = fixture.componentInstance;
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    localStorage.clear();
    if (component) {
      component.ngOnDestroy();
    }
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('sends the recovery OTP for a valid email', () => {
    spyOn(authService, 'sendForgotPasswordOtp').and.returnValue(of({ message: 'ok' }));
    component.email = 'user@example.com';

    component.sendOtp();

    expect(authService.sendForgotPasswordOtp).toHaveBeenCalledWith('user@example.com');
    expect(component.otpSent).toBeTrue();
    expect(component.resendCooldown).toBeGreaterThan(0);
  });

  it('validates the email before calling the backend', () => {
    spyOn(authService, 'sendForgotPasswordOtp').and.returnValue(of({ message: 'ok' }));
    component.email = 'not-an-email';

    component.sendOtp();

    expect(authService.sendForgotPasswordOtp).not.toHaveBeenCalled();
    expect(component.error).toContain('valid');
  });

  it('surfaces backend errors such as the resend cooldown', () => {
    spyOn(authService, 'sendForgotPasswordOtp')
      .and.returnValue(throwError(() => ({
        status: 429,
        error: 'Please wait 42 second(s) before requesting another OTP'
      })));
    component.email = 'user@example.com';

    component.sendOtp();

    expect(component.error).toContain('Please wait');
    expect(component.otpSent).toBeFalse();
  });

  it('authenticates through the backend and forces a password change', fakeAsync(() => {
    const navigateSpy = spyOn(router, 'navigate');
    component.email = 'user@example.com';
    component.otp = '123456';

    component.verifyOtp();

    const req = httpMock.expectOne(`${authService.apiUrl}/api/forgot-password/verify-otp`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'user@example.com', otp: '123456' });
    req.flush({
      token: 'jwt-token',
      username: 'user',
      userRole: 'User',
      userId: 1,
      mustChangePassword: true
    });
    flush();

    expect(component.message).toContain('logged in using OTP');
    expect(localStorage.getItem('token')).toBe('jwt-token');
    expect(localStorage.getItem('passwordRecoverySession')).toBe('true');
    expect(navigateSpy).toHaveBeenCalledWith(['/usernav/change-password']);
    httpMock.verify();
  }));

  it('keeps the user on the page for an invalid OTP', () => {
    const navigateSpy = spyOn(router, 'navigate');
    spyOn(authService, 'verifyForgotPasswordOtp')
      .and.returnValue(throwError(() => ({ status: 400, error: 'OTP is invalid or expired' })));
    component.email = 'user@example.com';
    component.otp = '999999';

    component.verifyOtp();

    expect(component.error).toBe('OTP is invalid or expired');
    expect(navigateSpy).not.toHaveBeenCalled();
  });
});
