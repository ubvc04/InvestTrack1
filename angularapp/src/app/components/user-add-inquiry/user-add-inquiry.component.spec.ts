import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserAddInquiryComponent } from './user-add-inquiry.component';

describe('UserAddInquiryComponent', () => {
  let component: UserAddInquiryComponent;
  let fixture: ComponentFixture<UserAddInquiryComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [UserAddInquiryComponent]
    });
    fixture = TestBed.createComponent(UserAddInquiryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
