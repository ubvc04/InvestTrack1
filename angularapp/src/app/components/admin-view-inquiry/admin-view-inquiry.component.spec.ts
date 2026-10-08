import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminViewInquiryComponent } from './admin-view-inquiry.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

describe('AdminViewInquiryComponent', () => {
  let component: AdminViewInquiryComponent;
  let fixture: ComponentFixture<AdminViewInquiryComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AdminViewInquiryComponent],
      imports: [HttpClientTestingModule, RouterTestingModule.withRoutes([]), FormsModule, ReactiveFormsModule]
    });
    fixture = TestBed.createComponent(AdminViewInquiryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
