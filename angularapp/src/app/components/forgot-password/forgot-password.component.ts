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
  otpVerified = false;
  resetToken = '';
  newPassword = '';
  confirmPassword = '';
  loading = false;
  updating = false;
  message = '';
  success = '';
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
    if (this.resendCooldown > 0 || this.loading || this.otpVerified) {
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
        // No login happens here: the backend only issued a short-lived reset
        // authorization. The password fields below stay hidden until this point.
        this.otpVerified = true;
        this.resetToken = response.resetToken;
        this.message = 'OTP verified. Set a new password below.';
      },
      error: err => {
        this.loading = false;
        this.error = this.extractError(err, 'Invalid or expired OTP.');
      }
    });
  }

  updatePassword(): void {
    this.error = '';
    this.success = '';
    if (!this.otpVerified || !this.resetToken) {
      this.error = 'Verify the OTP first.';
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.error = 'Passwords do not match';
      return;
    }
    if (this.newPassword.length < 8) {
      this.error = 'Password must be at least 8 characters long';
      return;
    }
    this.updating = true;
    this.authService.changeForgottenPassword({
      email: this.email,
      resetToken: this.resetToken,
      newPassword: this.newPassword,
      confirmPassword: this.confirmPassword
    }).subscribe({
      next: () => {
        this.updating = false;
        this.resetToken = '';
        this.newPassword = '';
        this.confirmPassword = '';
        this.success = 'Password updated successfully. Redirecting to Login...';
        // The user logs in manually with the new password; no auto-login.
        setTimeout(() => this.router.navigate(['/login']), 2000);
      },
      error: err => {
        this.updating = false;
        const message = this.extractError(err, 'Unable to update the password.');
        this.error = message;
        // The reset grant expired or was already used — require a fresh OTP round.
        if (message.toLowerCase().includes('expired')) {
          this.otpVerified = false;
          this.resetToken = '';
        }
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
