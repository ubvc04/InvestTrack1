import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (route, state) => {

  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isLoggedIn()) {
    router.navigate(['/login']);
    return false;
  }

  const expectedRole = route.data['role'];

  const allowedRoles = Array.isArray(expectedRole) ? expectedRole : [expectedRole];
  if (expectedRole && !allowedRoles.includes(authService.getUserRole())) {
    router.navigate(['/login']);
    return false;
  }

  // The backend rejects every authenticated call until the password is changed,
  // so keep the user on the change-password page while that requirement stands.
  if (authService.mustChangePasswordRequired() && !state.url.endsWith('/change-password')) {
    router.navigate([
      authService.getUserRole() === 'User'
        ? '/usernav/change-password'
        : '/adminnav/change-password'
    ]);
    return false;
  }

  return true;
};
