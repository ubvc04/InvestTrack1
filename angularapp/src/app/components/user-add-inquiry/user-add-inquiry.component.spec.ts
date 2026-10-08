import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserAddInquiryComponent } from './user-add-inquiry.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

describe('UserAddInquiryComponent', () => {
  let component: UserAddInquiryComponent;
  let fixture: ComponentFixture<UserAddInquiryComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [UserAddInquiryComponent],
      imports: [HttpClientTestingModule, RouterTestingModule.withRoutes([]), FormsModule, ReactiveFormsModule]
    });
    fixture = TestBed.createComponent(UserAddInquiryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
