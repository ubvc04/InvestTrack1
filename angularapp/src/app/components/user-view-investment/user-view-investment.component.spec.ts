import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserViewInvestmentComponent } from './user-view-investment.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

describe('UserViewInvestmentComponent', () => {
  let component: UserViewInvestmentComponent;
  let fixture: ComponentFixture<UserViewInvestmentComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [UserViewInvestmentComponent],
      imports: [HttpClientTestingModule, RouterTestingModule.withRoutes([]), FormsModule, ReactiveFormsModule]
    });
    fixture = TestBed.createComponent(UserViewInvestmentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
