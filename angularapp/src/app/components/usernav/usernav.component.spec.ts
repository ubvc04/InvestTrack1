import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UsernavComponent } from './usernav.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

describe('UsernavComponent', () => {
  let component: UsernavComponent;
  let fixture: ComponentFixture<UsernavComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [UsernavComponent],
      imports: [HttpClientTestingModule, RouterTestingModule.withRoutes([]), FormsModule, ReactiveFormsModule]
    });
    fixture = TestBed.createComponent(UsernavComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
