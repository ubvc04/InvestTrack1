import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminEditInvestmentComponent } from './admin-edit-investment.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

describe('AdminEditInvestmentComponent', () => {
  let component: AdminEditInvestmentComponent;
  let fixture: ComponentFixture<AdminEditInvestmentComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AdminEditInvestmentComponent],
      imports: [HttpClientTestingModule, RouterTestingModule.withRoutes([]), FormsModule, ReactiveFormsModule]
    });
    fixture = TestBed.createComponent(AdminEditInvestmentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
