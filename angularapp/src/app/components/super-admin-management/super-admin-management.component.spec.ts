import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SuperAdminManagementComponent } from './super-admin-management.component';

describe('SuperAdminManagementComponent', () => {
  let component: SuperAdminManagementComponent;
  let fixture: ComponentFixture<SuperAdminManagementComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [SuperAdminManagementComponent]
    });
    fixture = TestBed.createComponent(SuperAdminManagementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
