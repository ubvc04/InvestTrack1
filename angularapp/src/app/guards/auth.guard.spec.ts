import { TestBed } from '@angular/core/testing';
import { CanActivateFn, Router } from '@angular/router';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';

import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
      TestBed.runInInjectionContext(() => authGuard(...guardParameters));

  let authService: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, RouterTestingModule.withRoutes([])]
    });
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => localStorage.clear());

  it('should be created', () => {
    expect(executeGuard).toBeTruthy();
  });

  it('redirects anonymous users to login', () => {
    const navigateSpy = spyOn(router, 'navigate');

    const result = executeGuard(
      { data: {} } as any,
      { url: '/usernav/home' } as any
    );

    expect(result).toBeFalse();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });

  it('keeps a password-change-required session on the change-password page', () => {
    localStorage.setItem('token', 'jwt-token');
    localStorage.setItem('userRole', 'User');
    localStorage.setItem('mustChangePassword', 'true');
    const navigateSpy = spyOn(router, 'navigate');

    const blocked = executeGuard(
      { data: { role: 'User' } } as any,
      { url: '/usernav/home' } as any
    );
    const allowed = executeGuard(
      { data: { role: 'User' } } as any,
      { url: '/usernav/change-password' } as any
    );

    expect(blocked).toBeFalse();
    expect(navigateSpy).toHaveBeenCalledWith(['/usernav/change-password']);
    expect(allowed).toBeTrue();
  });

  it('allows a normal user session', () => {
    localStorage.setItem('token', 'jwt-token');
    localStorage.setItem('userRole', 'User');
    localStorage.setItem('mustChangePassword', 'false');

    const result = executeGuard(
      { data: { role: 'User' } } as any,
      { url: '/usernav/home' } as any
    );

    expect(result).toBeTrue();
  });

  it('rejects a user opening an admin route', () => {
    localStorage.setItem('token', 'jwt-token');
    localStorage.setItem('userRole', 'User');
    localStorage.setItem('mustChangePassword', 'false');
    const navigateSpy = spyOn(router, 'navigate');

    const result = executeGuard(
      { data: { role: ['Admin', 'SuperAdmin'] } } as any,
      { url: '/adminnav/home' } as any
    );

    expect(result).toBeFalse();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });
});
