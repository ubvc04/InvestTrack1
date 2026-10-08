import { Component } from '@angular/core';
import { FormGroup, FormBuilder, Validators, AbstractControl } from '@angular/forms';
import { Router } from '@angular/router';
import { User } from 'src/app/models/user.model';
import { AuthService } from 'src/app/services/auth.service';

@Component({
  selector: 'app-signup',
  templateUrl: './signup.component.html',
  styleUrls: ['./signup.component.css']
})
export class SignupComponent {

  signupForm: FormGroup;
  errorMessage = '';
  successMessage = '';
  showModal = false;
  loading = false;

  otp = '';
  otpSent = false;
  otpVerified = false;
  otpMessage = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {

    this.signupForm = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', Validators.required],
      mobileNumber: ['', [Validators.required, Validators.pattern('^[0-9]{10}$')]]
    }, { validators: this.passwordMatchValidator });
  }

  passwordMatchValidator(group: AbstractControl) {
    const pass = group.get('password')?.value;
    const confirm = group.get('confirmPassword')?.value;
    return pass === confirm ? null : { passwordMismatch: true };
  }

  get username() { return this.signupForm.get('username'); }
  get email() { return this.signupForm.get('email'); }
  get password() { return this.signupForm.get('password'); }
  get confirmPassword() { return this.signupForm.get('confirmPassword'); }
  get mobileNumber() { return this.signupForm.get('mobileNumber'); }

  sendOtp(): void {

    const email = this.signupForm.value.email;

    if (!email) {
      this.errorMessage = 'Please enter email first';
      return;
    }

    this.errorMessage = '';

    this.authService.sendOtp(email).subscribe({
      next: () => {
        this.otpSent = true;
        this.otpMessage = 'OTP sent successfully';
      },
      error: () => {
        this.errorMessage = 'Failed to send OTP';
      }
    });
  }

  verifyOtp(): void {

    const email = this.signupForm.value.email;

    if (!this.otp) {
      this.errorMessage = 'Please enter OTP';
      return;
    }

    this.authService.verifyOtp(
      email,
      this.otp
    ).subscribe({
      next: () => {

        this.otpVerified = true;

        this.otpMessage =
          'OTP verified successfully';

        this.errorMessage = '';
      },
      error: () => {

        this.otpVerified = false;

        this.errorMessage =
          'Invalid OTP';
      }
    });
  }

  onSignup(): void {

    if (this.signupForm.invalid) {
      return;
    }

    if (!this.otpVerified) {

      this.errorMessage =
        'Please verify OTP before registration';

      return;
    }

    this.loading = true;
    this.errorMessage = '';

    const user: User = {
      username: this.signupForm.value.username,
      email: this.signupForm.value.email,
      password: this.signupForm.value.password,
      mobileNumber: this.signupForm.value.mobileNumber,
      userRole: 'User'
    };

    this.authService.register(user).subscribe({
      next: () => {
        this.loading = false;
        this.showModal = true;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage =
          err?.error?.error ||
          err?.error?.message ||
          'Registration failed. Please try again.';
      }
    });
  }

  onModalOk(): void {
    this.showModal = false;
    this.router.navigate(['/login']);
  }

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}