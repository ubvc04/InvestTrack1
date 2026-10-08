import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl } from '@angular/forms';
import { User } from 'src/app/models/user.model';
import { AuthService } from 'src/app/services/auth.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-super-admin-management',
  templateUrl: './super-admin-management.component.html'
})
export class SuperAdminManagementComponent implements OnInit, OnDestroy {
  adminForm: FormGroup;
  admins: User[] = [];
  message = '';
  errorMessage = '';
  otp = '';
  otpSent = false;
  otpVerified = false;
  otpMessage = '';
  otpLoading = false;

  phoneOtp = '';
  phoneOtpSent = false;
  phoneOtpVerified = false;
  phoneOtpMessage = '';
  phoneOtpLoading = false;
  resendCooldown = 0;
  private cooldownTimer: any = null;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.adminForm = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', Validators.required],
      mobileNumber: ['', [Validators.required, Validators.pattern('^[0-9]{10}$')]]
    }, { validators: this.passwordMatchValidator });

    // Changing the Admin phone number invalidates a previously verified phone OTP.
    this.adminForm.get('mobileNumber')?.valueChanges.subscribe(() => {
      if (this.phoneOtpVerified || this.phoneOtpSent) {
        this.phoneOtpVerified = false;
        this.phoneOtpSent = false;
        this.phoneOtp = '';
        this.phoneOtpMessage = '';
      }
    });
  }

  ngOnInit(): void { this.loadData(); }

  ngOnDestroy(): void {
    if (this.cooldownTimer) {
      clearInterval(this.cooldownTimer);
    }
  }

  passwordMatchValidator(group: AbstractControl) {
    return group.get('password')?.value === group.get('confirmPassword')?.value
      ? null : { passwordMismatch: true };
  }

  loadData(): void {
    this.authService.getAdmins().subscribe({
      next: data => this.admins = data,
      error: err => this.handleManagementError(err)
    });
  }

  sendOtp(): void {
    const email = this.adminForm.get('email')?.value;
    if (!email || this.adminForm.get('email')?.invalid) {
      this.errorMessage = 'Enter a valid Admin email before requesting OTP.';
      return;
    }

    this.errorMessage = '';
    this.otpMessage = '';
    this.otpVerified = false;
    this.otp = '';
    this.otpLoading = true;
    this.authService.sendOtp(email).subscribe({
      next: () => {
        this.otpSent = true;
        this.otpMessage = 'OTP sent successfully. Check the Admin email.';
        this.otpLoading = false;
      },
      error: err => {
        this.otpLoading = false;
        this.errorMessage = this.extractError(err, 'Unable to send OTP.');
      }
    });
  }

  verifyOtp(): void {
    const email = this.adminForm.get('email')?.value;
    if (!this.otp) {
      this.errorMessage = 'Enter the OTP sent to the Admin email.';
      return;
    }

    this.authService.verifyOtp(email, this.otp).subscribe({
      next: () => {
        this.otpVerified = true;
        this.otpMessage = 'Admin email verified successfully.';
        this.errorMessage = '';
      },
      error: err => {
        this.otpVerified = false;
        this.errorMessage = this.extractError(err, 'Invalid OTP.');
      }
    });
  }

  sendPhoneOtp(): void {
    const phone = this.adminForm.get('mobileNumber')?.value;
    if (!phone || this.adminForm.get('mobileNumber')?.invalid) {
      this.errorMessage = 'Enter a valid Admin mobile number before requesting OTP.';
      return;
    }

    this.errorMessage = '';
    this.phoneOtpMessage = '';
    this.phoneOtpVerified = false;
    this.phoneOtp = '';
    this.phoneOtpLoading = true;
    this.authService.sendPhoneOtp(phone).subscribe({
      next: () => {
        this.phoneOtpLoading = false;
        this.phoneOtpSent = true;
        this.phoneOtpMessage = 'OTP sent to the Admin mobile number.';
        this.startResendCooldown();
      },
      error: err => {
        this.phoneOtpLoading = false;
        this.errorMessage = this.extractError(err, 'Unable to send phone OTP.');
      }
    });
  }

  verifyPhoneOtp(): void {
    const phone = this.adminForm.get('mobileNumber')?.value;
    if (!this.phoneOtp) {
      this.errorMessage = 'Enter the OTP sent to the Admin mobile number.';
      return;
    }

    this.errorMessage = '';
    this.phoneOtpLoading = true;
    this.authService.verifyPhoneOtp(phone, this.phoneOtp).subscribe({
      next: () => {
        this.phoneOtpLoading = false;
        this.phoneOtpVerified = true;
        this.phoneOtpMessage = '';
        this.errorMessage = '';
      },
      error: err => {
        this.phoneOtpLoading = false;
        this.phoneOtpVerified = false;
        this.errorMessage = this.extractError(err, 'Invalid or expired phone OTP.');
      }
    });
  }

  createAdmin(): void {
    this.message = '';
    this.errorMessage = '';
    if (this.adminForm.invalid) {
      this.adminForm.markAllAsTouched();
      return;
    }
    if (!this.otpVerified) {
      this.errorMessage = 'Verify the Admin email before creating the account.';
      return;
    }
    if (!this.phoneOtpVerified) {
      this.errorMessage = 'Verify the Admin phone number with OTP before creating the account.';
      return;
    }
    const user: User = { ...this.adminForm.value, userRole: 'Admin' };
    this.authService.createAdmin(user).subscribe({
      next: () => {
        this.message = 'Admin account created successfully.';
        this.adminForm.reset();
        this.otp = '';
        this.otpSent = false;
        this.otpVerified = false;
        this.otpMessage = '';
        this.phoneOtp = '';
        this.phoneOtpSent = false;
        this.phoneOtpVerified = false;
        this.phoneOtpMessage = '';
        this.loadData();
      },
      error: err => {
        // The backend consumes the verified phone number, so it must be re-verified.
        this.phoneOtpVerified = false;
        this.handleManagementError(err);
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

  private handleManagementError(err: any): void {
    const status = err?.status;
    const serverMessage = typeof err?.error === 'string'
      ? err.error
      : err?.error?.message || err?.error?.error;

    if (status === 403 && serverMessage?.toLowerCase().includes('initial password')) {
      this.errorMessage = 'Change the SuperAdmin initial password before using Admin Management.';
      setTimeout(() => this.router.navigate(['/adminnav/change-password']), 1200);
      return;
    }

    this.errorMessage = serverMessage || 'Admin management data could not be loaded.';
  }
}
