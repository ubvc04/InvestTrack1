import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { InvestmentInquiry } from 'src/app/models/investment-inquiry.model';
import { AuthService } from 'src/app/services/auth.service';
import { InvestmentInquiryService } from 'src/app/services/investment-inquiry.service';

@Component({
  selector: 'app-user-view-inquiries',
  templateUrl: './user-view-inquiries.component.html',
  styleUrls: ['./user-view-inquiries.component.css']
})
export class UserViewInquiriesComponent implements OnInit {

  inquiries: InvestmentInquiry[] = [];
  loading = true;

  constructor(
    private inquiryService: InvestmentInquiryService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.inquiryService
      .getInquiriesByUserId(this.authService.getUserId())
      .subscribe({
        next: data => {
          this.inquiries = data;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
          this.router.navigate(['/error']);
        }
      });
  }

  getStatusClass(status?: string): string {
    return status === 'RESOLVED'
      ? 'bg-success'
      : 'bg-warning text-dark';
  }
}