import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserViewInquiriesComponent } from './user-view-inquiries.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

describe('UserViewInquiriesComponent', () => {
  let component: UserViewInquiriesComponent;
  let fixture: ComponentFixture<UserViewInquiriesComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [UserViewInquiriesComponent],
      imports: [HttpClientTestingModule, RouterTestingModule.withRoutes([]), FormsModule, ReactiveFormsModule]
    });
    fixture = TestBed.createComponent(UserViewInquiriesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
