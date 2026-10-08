import { Component, OnInit } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { InvestmentService } from 'src/app/services/investment.service';

@Component({
  selector: 'app-admin-edit-investment',
  templateUrl: './admin-edit-investment.component.html',
  styleUrls: ['./admin-edit-investment.component.css']
})
export class AdminEditInvestmentComponent  implements OnInit {
  editForm: FormGroup;
  investmentId!: number;
  showModal = false;
  errorMessage = '';
  loading = false;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private investmentService: InvestmentService
  ) {
    this.editForm = this.fb.group({
      name:          ['', Validators.required],
      description:   ['', Validators.required],
      type:          ['', Validators.required],
      purchasePrice: ['', [Validators.required, Validators.min(0.01)]],
      currentPrice:  ['', [Validators.required, Validators.min(0.01)]],
      quantity:      ['', [Validators.required, Validators.min(1)]],
      purchaseDate:  ['', Validators.required],
      status:        ['', Validators.required]
    });
  }

  ngOnInit(): void {
    this.investmentId = Number(this.route.snapshot.paramMap.get('id'));
    this.investmentService.getInvestmentById(this.investmentId).subscribe({
      next: (inv) => this.editForm.patchValue(inv),
      error: () => this.router.navigate(['/error'])
    });
  }

  get f() { return this.editForm.controls; }

  onUpdate(): void {
    if (this.editForm.invalid) return;
    this.loading = true;
    this.errorMessage = '';
    this.investmentService.updateInvestment(this.investmentId, this.editForm.value).subscribe({
      next: () => { this.loading = false; this.showModal = true; },
      error: (err) => { this.loading = false; this.errorMessage = err?.error?.error || 'Update failed.'; }
    });
  }

  onOk(): void {
    this.showModal = false;
    this.router.navigate(['/adminnav/view-investment']);
  }
}