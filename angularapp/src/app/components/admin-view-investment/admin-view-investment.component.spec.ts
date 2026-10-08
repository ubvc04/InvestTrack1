import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminViewInvestmentComponent } from './admin-view-investment.component';

describe('AdminViewInvestmentComponent', () => {
  let component: AdminViewInvestmentComponent;
  let fixture: ComponentFixture<AdminViewInvestmentComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AdminViewInvestmentComponent]
    });
    fixture = TestBed.createComponent(AdminViewInvestmentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
