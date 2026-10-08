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

  return true;
};