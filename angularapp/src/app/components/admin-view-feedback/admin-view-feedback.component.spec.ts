import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminViewFeedbackComponent } from './admin-view-feedback.component';

describe('AdminViewFeedbackComponent', () => {
  let component: AdminViewFeedbackComponent;
  let fixture: ComponentFixture<AdminViewFeedbackComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AdminViewFeedbackComponent]
    });
    fixture = TestBed.createComponent(AdminViewFeedbackComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
