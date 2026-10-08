import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminViewInvestmentComponent } from './admin-view-investment.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

describe('AdminViewInvestmentComponent', () => {
  let component: AdminViewInvestmentComponent;
  let fixture: ComponentFixture<AdminViewInvestmentComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AdminViewInvestmentComponent],
      imports: [HttpClientTestingModule, RouterTestingModule.withRoutes([]), FormsModule, ReactiveFormsModule]
    });
    fixture = TestBed.createComponent(AdminViewInvestmentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
