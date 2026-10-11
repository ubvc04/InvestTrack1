import { Component, OnDestroy } from '@angular/core';
import { FormGroup, FormBuilder, Validators, AbstractControl } from '@angular/forms';
import { Router } from '@angular/router';
import { User } from 'src/app/models/user.model';
import { AuthService } from 'src/app/services/auth.service';

@Component({
  selector: 'app-signup',
  templateUrl: './signup.component.html',
  styleUrls: ['./signup.component.css']
})
export class SignupComponent implements OnDestroy {

  signupForm: FormGroup;
  errorMessage = '';
  successMessage = '';
  showModal = false;
  loading = false;

  /** Email-specific error (format / already registered) — shown directly
   *  under the Email field, never near the Create Account button. */
  emailError = '';

  otp = '';
  otpSent = false;
  otpVerified = false;
  otpMessage = '';
  otpLoading = false;
  otpVerifying = false;

  showPassword = false;
  showConfirmPassword = false;

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

    this.signupForm = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8), SignupComponent.strengthValidator]],
      confirmPassword: ['', Validators.required],
      mobileNumber: ['', [Validators.required, Validators.pattern('^[0-9]{10}$')]]
    }, { validators: this.passwordMatchValidator });

    // Changing the email invalidates any previously sent or verified email OTP
    // and clears the previous email error so the message never goes stale.
    this.signupForm.get('email')?.valueChanges.subscribe(() => {
      this.emailError = '';
      if (this.otpVerified || this.otpSent) {
        this.otpVerified = false;
        this.otpSent = false;
        this.otp = '';
        this.otpMessage = '';
      }
    });

    // Changing the phone number invalidates any previously verified phone OTP.
    this.signupForm.get('mobileNumber')?.valueChanges.subscribe(() => {
      if (this.phoneOtpVerified || this.phoneOtpSent) {
        this.phoneOtpVerified = false;
        this.phoneOtpSent = false;
        this.phoneOtp = '';
        this.phoneOtpMessage = '';
      }
    });
  }

  ngOnDestroy(): void {
    if (this.cooldownTimer) {
      clearInterval(this.cooldownTimer);
    }
  }

  passwordMatchValidator(group: AbstractControl) {
    const pass = group.get('password')?.value;
    const confirm = group.get('confirmPassword')?.value;
    return pass === confirm ? null : { passwordMismatch: true };
  }

  /** Mirrors the backend registration password policy. */
  private static strengthValidator(control: AbstractControl) {
    const value: string = control.value || '';
    const met =
      value.length >= 8 &&
      /[A-Z]/.test(value) &&
      /[a-z]/.test(value) &&
      /[0-9]/.test(value) &&
      /[^A-Za-z0-9]/.test(value);
    return met ? null : { passwordStrength: true };
  }

  get username() { return this.signupForm.get('username'); }
  get email() { return this.signupForm.get('email'); }
  get password() { return this.signupForm.get('password'); }
  get confirmPassword() { return this.signupForm.get('confirmPassword'); }
  get mobileNumber() { return this.signupForm.get('mobileNumber'); }

  /** Live per-rule password checks — recomputed on every change-detection
   *  cycle so the requirement list updates while the user types. */
  get passwordChecks() {
    const value: string = this.password?.value || '';
    return {
      length: value.length >= 8,
      upper: /[A-Z]/.test(value),
      lower: /[a-z]/.test(value),
      number: /[0-9]/.test(value),
      special: /[^A-Za-z0-9]/.test(value)
    };
  }

  sendOtp(): void {

    const email = this.signupForm.value.email;

    if (!email) {
      this.emailError = 'Please enter email first';
      return;
    }

    if (this.email?.invalid) {
      this.emailError = 'Enter a valid email address before requesting OTP';
      return;
    }

    this.emailError = '';
    this.otpMessage = '';
    this.otpLoading = true;

    // The backend validates the email, rejects addresses that are already
    // registered (409) and enforces the resend cooldown before sending.
    this.authService.sendOtp(email).subscribe({
      next: () => {
        this.otpLoading = false;
        this.emailError = '';
        this.otpSent = true;
        this.otpMessage = 'OTP sent successfully. Check your email for the code.';
      },
      error: err => {
        this.otpLoading = false;
        this.emailError = this.extractError(err, 'Failed to send OTP');
      }
    });
  }

  verifyOtp(): void {

    const email = this.signupForm.value.email;

    if (!this.otp) {
      this.errorMessage = 'Please enter OTP';
      return;
    }

    this.errorMessage = '';
    this.otpVerifying = true;

    this.authService.verifyOtp(
      email,
      this.otp
    ).subscribe({
      next: () => {

        this.otpVerifying = false;
        this.otpVerified = true;

        this.otpMessage =
          'OTP verified successfully';

        this.errorMessage = '';
      },
      error: err => {

        this.otpVerifying = false;
        this.otpVerified = false;

        this.errorMessage =
          this.extractError(err, 'Invalid OTP');
      }
    });
  }

  sendPhoneOtp(): void {
    const phone = this.signupForm.value.mobileNumber;

    if (!phone || this.mobileNumber?.invalid) {
      this.errorMessage = 'Enter a valid 10-digit mobile number before requesting OTP';
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
        this.phoneOtpMessage = 'OTP sent to your mobile number.';
        this.startResendCooldown();
      },
      error: err => {
        this.phoneOtpLoading = false;
        this.errorMessage = this.extractError(err, 'Unable to send phone OTP. Please try again.');
      }
    });
  }

  verifyPhoneOtp(): void {
    const phone = this.signupForm.value.mobileNumber;

    if (!this.phoneOtp) {
      this.errorMessage = 'Enter the OTP sent to your mobile number';
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
        this.errorMessage = this.extractError(err, 'Invalid or expired OTP.');
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

    if (!this.phoneOtpVerified) {
      this.errorMessage = 'Please verify your mobile number with OTP before registration';
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
        this.phoneOtpVerified = false;
        const message = this.extractError(err, 'Registration failed. Please try again.');
        // The duplicate-email race (409 from the unique constraint) belongs
        // under the Email field, not in the generic error slot.
        if (message.toLowerCase().includes('already registered')) {
          this.emailError = message;
        } else {
          this.errorMessage = message;
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

  onModalOk(): void {
    this.showModal = false;
    this.router.navigate(['/login']);
  }

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}