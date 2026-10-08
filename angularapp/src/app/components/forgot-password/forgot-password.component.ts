import { Component, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-forgot-password',
  templateUrl: './forgot-password.component.html'
})
export class ForgotPasswordComponent implements OnDestroy {
  email = '';
  otp = '';
  otpSent = false;
  loading = false;
  message = '';
  error = '';
  resendCooldown = 0;
  private cooldownTimer: any = null;

  constructor(private authService: AuthService, private router: Router) {}

  ngOnDestroy(): void {
    if (this.cooldownTimer) {
      clearInterval(this.cooldownTimer);
    }
  }

  sendOtp(): void {
    this.error = '';
    this.message = '';
    if (!this.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email)) {
      this.error = 'Enter a valid registered email address.';
      return;
    }
    this.loading = true;
    this.authService.sendForgotPasswordOtp(this.email).subscribe({
      next: () => {
        this.loading = false;
        this.otpSent = true;
        this.message = 'OTP sent. Check your email.';
        this.startResendCooldown();
      },
      error: err => {
        this.loading = false;
        this.error = this.extractError(err, 'Unable to send recovery OTP.');
      }
    });
  }

  resendOtp(): void {
    if (this.resendCooldown > 0 || this.loading) {
      return;
    }
    this.sendOtp();
  }

  verifyOtp(): void {
    this.error = '';
    if (!/^\d{6}$/.test(this.otp)) {
      this.error = 'Enter the six-digit OTP.';
      return;
    }
    this.loading = true;
    this.authService.verifyForgotPasswordOtp(this.email, this.otp).subscribe({
      next: response => {
        this.loading = false;
        this.otp = '';
        // The backend already issued the JWT and flagged mustChangePassword.
        this.message = 'You logged in using OTP. For security, please change your password.';
        const changePasswordUrl = response.userRole === 'User'
          ? '/usernav/change-password'
          : '/adminnav/change-password';
        setTimeout(() => this.router.navigate([changePasswordUrl]), 1500);
      },
      error: err => {
        this.loading = false;
        this.error = this.extractError(err, 'Invalid or expired OTP.');
      }
    });
  }

  private startResendCooldown(): void {
    if (this.cooldownTimer) {
      clearInterval(this.cooldownTimer);
    }
    this.resendCooldown = 60;
    this.cooldownTimer = setInterval(() => {
      this.resendCooldown--;
      if (this.resendCooldown <= 0) {
        clearInterval(this.cooldownTimer);
        this.cooldownTimer = null;
      }
    }, 1000);
  }

  private extractError(err: any, fallback: string): string {
    if (typeof err?.error === 'string' && err.error.trim()) {
      return err.error;
    }
    if (typeof err?.error?.error === 'string' && err.error.error.trim()) {
      return err.error.error;
    }
    if (typeof err?.error?.message === 'string' && err.error.message.trim()) {
      return err.error.message;
    }
    if (err?.status === 0) {
      return 'Network error. Please check your connection and try again.';
    }
    return fallback;
  }
}
