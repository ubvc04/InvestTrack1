import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminEditInvestmentComponent } from './admin-edit-investment.component';

describe('AdminEditInvestmentComponent', () => {
  let component: AdminEditInvestmentComponent;
  let fixture: ComponentFixture<AdminEditInvestmentComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AdminEditInvestmentComponent]
    });
    fixture = TestBed.createComponent(AdminEditInvestmentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
