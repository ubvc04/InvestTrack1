import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserAddFeedbackComponent } from './user-add-feedback.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

describe('UserAddFeedbackComponent', () => {
  let component: UserAddFeedbackComponent;
  let fixture: ComponentFixture<UserAddFeedbackComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [UserAddFeedbackComponent],
      imports: [HttpClientTestingModule, RouterTestingModule.withRoutes([]), FormsModule, ReactiveFormsModule]
    });
    fixture = TestBed.createComponent(UserAddFeedbackComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
