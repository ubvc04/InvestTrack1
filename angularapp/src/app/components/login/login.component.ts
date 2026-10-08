import { Component } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Login } from 'src/app/models/login.model';
import { AuthService } from 'src/app/services/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent { 
  loginForm: FormGroup;
errorMessage = '';
loading = false;
showPassword = false;
readonly ringSegments = Array.from({ length: 48 }, (_, index) => index);
readonly accentSegments = new Set([0, 7, 13, 20, 27, 34, 41]);

constructor(
  private fb: FormBuilder,
  private authService: AuthService,
  private router: Router
) {
  this.loginForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(4)]]
  });
}

get email() { return this.loginForm.get('email'); }
get password() { return this.loginForm.get('password'); }

onLogin(): void {
  if (this.loginForm.invalid) {
    this.loginForm.markAllAsTouched();
    return;
  }
  this.loading = true;
  this.errorMessage = '';

  const login: Login = this.loginForm.value;
  this.authService.login(login).subscribe({
    next: (response) => {
      this.loading = false;
      if (response.mustChangePassword) {
        this.router.navigate([response.userRole === 'User' ? '/usernav/change-password' : '/adminnav/change-password']);
      } else if (response.userRole === 'Admin' || response.userRole === 'SuperAdmin') {
        this.router.navigate(['/adminnav/home']);
      } else {
        this.router.navigate(['/usernav/home']);
      }
    },
    error: () => {
      this.loading = false;
      this.errorMessage = 'Invalid Email or Password. Please try again.';
    }
  });
}

goToSignup(): void {
  this.router.navigate(['/signup']);
}

isAccentSegment(index: number): boolean {
  return this.accentSegments.has(index);
}

segmentStyle(index: number): { [key: string]: string } {
  return {
    transform: `rotate(${index * 7.5}deg) translateY(-190px)`,
    'animation-delay': `${(index % 12) * -0.18}s`
  };
}
}