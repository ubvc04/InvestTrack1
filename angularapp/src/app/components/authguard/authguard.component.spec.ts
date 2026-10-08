import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AuthguardComponent } from './authguard.component';

describe('AuthguardComponent', () => {
  let component: AuthguardComponent;
  let fixture: ComponentFixture<AuthguardComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AuthguardComponent]
    });
    fixture = TestBed.createComponent(AuthguardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
