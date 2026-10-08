import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminViewInquiryComponent } from './admin-view-inquiry.component';

describe('AdminViewInquiryComponent', () => {
  let component: AdminViewInquiryComponent;
  let fixture: ComponentFixture<AdminViewInquiryComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AdminViewInquiryComponent]
    });
    fixture = TestBed.createComponent(AdminViewInquiryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
