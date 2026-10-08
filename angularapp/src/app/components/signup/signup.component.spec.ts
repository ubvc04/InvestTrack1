import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';

import { SignupComponent } from './signup.component';
import { AuthService } from 'src/app/services/auth.service';

describe('SignupComponent', () => {
  let component: SignupComponent;
  let fixture: ComponentFixture<SignupComponent>;
  let authService: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [SignupComponent],
      imports: [
        FormsModule,
        ReactiveFormsModule,
        HttpClientTestingModule,
        RouterTestingModule.withRoutes([])
      ]
    });
    fixture = TestBed.createComponent(SignupComponent);
    component = fixture.componentInstance;
    authService = TestBed.inject(AuthService);
    fixture.detectChanges();
  });

  afterEach(() => localStorage.clear());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('blocks registration until the phone number is verified', () => {
    spyOn(authService, 'register').and.returnValue(of({} as any));
    component.signupForm.setValue({
      username: 'newuser',
      email: 'new@user.com',
      password: 'Secret@123',
      confirmPassword: 'Secret@123',
      mobileNumber: '9876543210'
    });
    component.otpVerified = true;
    component.phoneOtpVerified = false;

    component.onSignup();

    expect(component.errorMessage).toContain('mobile number');
    expect(authService.register).not.toHaveBeenCalled();
  });

  it('sends the phone OTP for the entered number', () => {
    spyOn(authService, 'sendPhoneOtp').and.returnValue(of({ message: 'ok' }));
    component.signupForm.get('mobileNumber')?.setValue('9876543210');

    component.sendPhoneOtp();

    expect(authService.sendPhoneOtp).toHaveBeenCalledWith('9876543210');
    expect(component.phoneOtpSent).toBeTrue();
    expect(component.resendCooldown).toBeGreaterThan(0);
  });

  it('rejects an invalid phone number before calling the backend', () => {
    spyOn(authService, 'sendPhoneOtp').and.returnValue(of({ message: 'ok' }));
    component.signupForm.get('mobileNumber')?.setValue('123');

    component.sendPhoneOtp();

    expect(authService.sendPhoneOtp).not.toHaveBeenCalled();
    expect(component.errorMessage).toContain('mobile number');
  });

  it('reports backend failures when verifying the phone OTP', () => {
    spyOn(authService, 'verifyPhoneOtp')
      .and.returnValue(throwError(() => ({ status: 400, error: 'Invalid or expired phone OTP' })));
    component.signupForm.get('mobileNumber')?.setValue('9876543210');
    component.phoneOtp = '000000';

    component.verifyPhoneOtp();

    expect(component.phoneOtpVerified).toBeFalse();
    expect(component.errorMessage).toBe('Invalid or expired phone OTP');
  });

  it('clears a verified phone when the number is changed', () => {
    component.signupForm.get('mobileNumber')?.setValue('9876543210');
    component.phoneOtpSent = true;
    component.phoneOtpVerified = true;

    component.signupForm.get('mobileNumber')?.setValue('9123456789');

    expect(component.phoneOtpVerified).toBeFalse();
    expect(component.phoneOtpSent).toBeFalse();
    expect(component.phoneOtp).toBe('');
  });
});
