import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';

import { SuperAdminManagementComponent } from './super-admin-management.component';
import { AuthService } from 'src/app/services/auth.service';

describe('SuperAdminManagementComponent', () => {
  let component: SuperAdminManagementComponent;
  let fixture: ComponentFixture<SuperAdminManagementComponent>;
  let authService: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [SuperAdminManagementComponent],
      imports: [
        FormsModule,
        ReactiveFormsModule,
        HttpClientTestingModule,
        RouterTestingModule.withRoutes([])
      ]
    });
    fixture = TestBed.createComponent(SuperAdminManagementComponent);
    component = fixture.componentInstance;
    authService = TestBed.inject(AuthService);
    spyOn(authService, 'getAdmins').and.returnValue(of([]));
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('does not create an admin before the phone number is verified', () => {
    spyOn(authService, 'createAdmin').and.returnValue(of({} as any));
    component.adminForm.setValue({
      username: 'adminuser',
      email: 'admin@user.com',
      password: 'Secret@123',
      confirmPassword: 'Secret@123',
      mobileNumber: '9876543210'
    });
    component.otpVerified = true;
    component.phoneOtpVerified = false;

    component.createAdmin();

    expect(component.errorMessage).toContain('phone number');
    expect(authService.createAdmin).not.toHaveBeenCalled();
  });

  it('does not create an admin before the email is verified', () => {
    spyOn(authService, 'createAdmin').and.returnValue(of({} as any));
    component.adminForm.setValue({
      username: 'adminuser',
      email: 'admin@user.com',
      password: 'Secret@123',
      confirmPassword: 'Secret@123',
      mobileNumber: '9876543210'
    });
    component.otpVerified = false;
    component.phoneOtpVerified = true;

    component.createAdmin();

    expect(component.errorMessage).toContain('email');
    expect(authService.createAdmin).not.toHaveBeenCalled();
  });

  it('creates the admin once both verifications succeeded', () => {
    spyOn(authService, 'createAdmin').and.returnValue(of({} as any));
    component.adminForm.setValue({
      username: 'adminuser',
      email: 'admin@user.com',
      password: 'Secret@123',
      confirmPassword: 'Secret@123',
      mobileNumber: '9876543210'
    });
    component.otpVerified = true;
    component.phoneOtpVerified = true;

    component.createAdmin();

    expect(authService.createAdmin).toHaveBeenCalled();
    expect(component.message).toContain('created successfully');
  });

  it('requests a phone OTP for the entered Admin number', () => {
    spyOn(authService, 'sendPhoneOtp').and.returnValue(of({ message: 'ok' }));
    component.adminForm.get('mobileNumber')?.setValue('9876543210');

    component.sendPhoneOtp();

    expect(authService.sendPhoneOtp).toHaveBeenCalledWith('9876543210');
    expect(component.phoneOtpSent).toBeTrue();
  });

  it('clears a verified phone when the Admin number is changed', () => {
    component.adminForm.get('mobileNumber')?.setValue('9876543210');
    component.phoneOtpSent = true;
    component.phoneOtpVerified = true;

    component.adminForm.get('mobileNumber')?.setValue('9123456789');

    expect(component.phoneOtpVerified).toBeFalse();
    expect(component.phoneOtpSent).toBeFalse();
  });
});
