import { Component } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { InvestmentService } from 'src/app/services/investment.service';

@Component({
  selector: 'app-admin-add-investment',
  templateUrl: './admin-add-investment.component.html',
  styleUrls: ['./admin-add-investment.component.css']
})
export class AdminAddInvestmentComponent {
  investmentForm: FormGroup;
  showModal = false;
  errorMessage = '';
  loading = false;

  constructor(private fb: FormBuilder, private investmentService: InvestmentService, private router: Router) {
    this.investmentForm = this.fb.group({
      name:          ['', [Validators.required]],
      description:   ['', [Validators.required]],
      type:          ['', [Validators.required]],
      purchasePrice: ['', [Validators.required, Validators.min(0.01)]],
      currentPrice:  ['', [Validators.required, Validators.min(0.01)]],
      quantity:      ['', [Validators.required, Validators.min(1)]],
      purchaseDate:  ['', [Validators.required]],
      status:        ['', [Validators.required]]
    });
  }

  get f() { return this.investmentForm.controls; }

  onSubmit(): void {
    if (this.investmentForm.invalid) return;
    this.loading = true;
    this.errorMessage = '';
    this.investmentService.addInvestment(this.investmentForm.value).subscribe({
      next: () => { this.loading = false; this.showModal = true; },
      error: (err) => { this.loading = false; this.errorMessage = err?.error?.error || 'Failed to add investment.'; }
    });
  }

  onClose(): void {
    this.showModal = false;
    this.investmentForm.reset();
  }

  goToView(): void {
    this.router.navigate(['/adminnav/view-investment']);
  }
}