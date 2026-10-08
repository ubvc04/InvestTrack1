import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl } from '@angular/forms';
import { User } from 'src/app/models/user.model';
import { AuthService } from 'src/app/services/auth.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-super-admin-management',
  templateUrl: './super-admin-management.component.html'
})
export class SuperAdminManagementComponent implements OnInit {
  adminForm: FormGroup;
  admins: User[] = [];
  message = '';
  errorMessage = '';
  otp = '';
  otpSent = false;
  otpVerified = false;
  otpMessage = '';
  otpLoading = false;

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
  }

  ngOnInit(): void { this.loadData(); }

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
        this.errorMessage = err?.error?.message || 'Unable to send OTP.';
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
      error: () => {
        this.otpVerified = false;
        this.errorMessage = 'Invalid OTP.';
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
    const user: User = { ...this.adminForm.value, userRole: 'Admin' };
    this.authService.createAdmin(user).subscribe({
      next: () => {
        this.message = 'Admin account created successfully.';
        this.adminForm.reset();
        this.otp = '';
        this.otpSent = false;
        this.otpVerified = false;
        this.otpMessage = '';
        this.loadData();
      },
      error: err => this.handleManagementError(err)
    });
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
