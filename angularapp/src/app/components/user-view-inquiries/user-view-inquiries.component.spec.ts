import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserViewInquiriesComponent } from './user-view-inquiries.component';

describe('UserViewInquiriesComponent', () => {
  let component: UserViewInquiriesComponent;
  let fixture: ComponentFixture<UserViewInquiriesComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [UserViewInquiriesComponent]
    });
    fixture = TestBed.createComponent(UserViewInquiriesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
