import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAddInvestmentComponent } from './admin-add-investment.component';

describe('AdminAddInvestmentComponent', () => {
  let component: AdminAddInvestmentComponent;
  let fixture: ComponentFixture<AdminAddInvestmentComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AdminAddInvestmentComponent]
    });
    fixture = TestBed.createComponent(AdminAddInvestmentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
