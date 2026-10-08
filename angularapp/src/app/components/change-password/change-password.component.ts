import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-change-password',
  templateUrl: './change-password.component.html',
  styleUrls: ['./change-password.component.css']
})
export class ChangePasswordComponent {

  oldPassword = '';
  newPassword = '';
  confirmPassword = '';

  message = '';
  error = '';

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  changePassword(): void {

    this.error = '';
    this.message = '';

    // Validate passwords match
    if (this.newPassword !== this.confirmPassword) {
      this.error = 'Passwords do not match';
      return;
    }

    // Validate minimum length
    if (this.newPassword.length < 8) {
      this.error = 'Password must be at least 8 characters long';
      return;
    }

    // Prevent using same password
    if (this.oldPassword === this.newPassword) {
      this.error = 'New password cannot be the same as old password';
      return;
    }

    const payload = {
      userId: this.authService.getUserId(),
      oldPassword: this.oldPassword,
      newPassword: this.newPassword,
      confirmPassword: this.confirmPassword
    };

    this.authService.changePassword(payload).subscribe({

      next: () => {

        this.message =
          '✅ Password changed successfully. Redirecting to Login...';

        setTimeout(() => {

          this.authService.logout();

          this.router.navigate(['/login']);

        }, 2000);

      },

      error: (err) => {

        console.log('Change Password Error:', err);

        if (typeof err.error === 'string') {
          this.error = err.error;
        }
        else if (err.error?.error) {
          this.error = err.error.error;
        }
        else if (err.message) {
          this.error = err.message;
        }
        else {
          this.error = 'Password update failed';
        }

      }

    });
  }
}