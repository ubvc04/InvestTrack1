import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminConsoleComponent } from './admin-console.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

describe('AdminConsoleComponent', () => {
  let component: AdminConsoleComponent;
  let fixture: ComponentFixture<AdminConsoleComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AdminConsoleComponent],
      imports: [HttpClientTestingModule, RouterTestingModule.withRoutes([]), FormsModule, ReactiveFormsModule]
    });
    fixture = TestBed.createComponent(AdminConsoleComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
