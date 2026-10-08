import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminConsoleComponent } from './admin-console.component';

describe('AdminConsoleComponent', () => {
  let component: AdminConsoleComponent;
  let fixture: ComponentFixture<AdminConsoleComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AdminConsoleComponent]
    });
    fixture = TestBed.createComponent(AdminConsoleComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
