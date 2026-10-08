import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';

import { ChangePasswordComponent } from './change-password.component';
import { AuthService } from 'src/app/services/auth.service';

describe('ChangePasswordComponent', () => {
  let component: ChangePasswordComponent;
  let fixture: ComponentFixture<ChangePasswordComponent>;
  let authService: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ChangePasswordComponent],
      imports: [
        FormsModule,
        HttpClientTestingModule,
        RouterTestingModule.withRoutes([])
      ]
    });
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => localStorage.clear());

  function createComponent(): void {
    fixture = TestBed.createComponent(ChangePasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('should create', () => {
    createComponent();
    expect(component).toBeTruthy();
    expect(component.isRecoverySession).toBeFalse();
  });

  it('uses the recovery endpoint without a current password after an OTP login', () => {
    localStorage.setItem('passwordRecoverySession', 'true');
    const forgottenSpy = spyOn(authService, 'changeForgottenPassword')
      .and.returnValue(of({ message: 'Password updated successfully' }));
    const normalSpy = spyOn(authService, 'changePassword')
      .and.returnValue(of({ message: 'Password updated successfully' }));
    createComponent();

    component.newPassword = 'NewPass@123';
    component.confirmPassword = 'NewPass@123';
    component.changePassword();

    expect(component.isRecoverySession).toBeTrue();
    expect(forgottenSpy).toHaveBeenCalledWith({
      newPassword: 'NewPass@123',
      confirmPassword: 'NewPass@123'
    });
    expect(normalSpy).not.toHaveBeenCalled();
    expect(component.message).toContain('changed successfully');
  });

  it('keeps the existing current-password flow for normal sessions', () => {
    const forgottenSpy = spyOn(authService, 'changeForgottenPassword')
      .and.returnValue(of({ message: 'Password updated successfully' }));
    const normalSpy = spyOn(authService, 'changePassword')
      .and.returnValue(of({ message: 'Password updated successfully' }));
    createComponent();

    component.oldPassword = 'OldPass@123';
    component.newPassword = 'NewPass@123';
    component.confirmPassword = 'NewPass@123';
    component.changePassword();

    expect(component.isRecoverySession).toBeFalse();
    expect(normalSpy).toHaveBeenCalled();
    expect(forgottenSpy).not.toHaveBeenCalled();
  });

  it('rejects mismatched passwords before calling the backend', () => {
    const normalSpy = spyOn(authService, 'changePassword')
      .and.returnValue(of({ message: 'Password updated successfully' }));
    createComponent();

    component.newPassword = 'NewPass@123';
    component.confirmPassword = 'Different@123';
    component.changePassword();

    expect(component.error).toBe('Passwords do not match');
    expect(normalSpy).not.toHaveBeenCalled();
  });
});
