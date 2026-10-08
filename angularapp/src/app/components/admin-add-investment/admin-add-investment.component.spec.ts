import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAddInvestmentComponent } from './admin-add-investment.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

describe('AdminAddInvestmentComponent', () => {
  let component: AdminAddInvestmentComponent;
  let fixture: ComponentFixture<AdminAddInvestmentComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AdminAddInvestmentComponent],
      imports: [HttpClientTestingModule, RouterTestingModule.withRoutes([]), FormsModule, ReactiveFormsModule]
    });
    fixture = TestBed.createComponent(AdminAddInvestmentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
