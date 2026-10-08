import { Component, OnDestroy, OnInit } from '@angular/core';
import { Feedback } from 'src/app/models/feedback.model';
import { InvestmentInquiry } from 'src/app/models/investment-inquiry.model';
import { Investment } from 'src/app/models/investment.model';
import { FeedbackService } from 'src/app/services/feedback.service';
import { InvestmentInquiryService } from 'src/app/services/investment-inquiry.service';
import { InvestmentService } from 'src/app/services/investment.service';

@Component({
  selector: 'app-admin-console',
  templateUrl: './admin-console.component.html',
  styleUrls: ['./admin-console.component.css']
})
export class AdminConsoleComponent implements OnDestroy, OnInit {
  investments: Investment[] = [];
  inquiries: InvestmentInquiry[] = [];
  feedbacks: Feedback[] = [];
  loading = true;
  refreshing = false;
  errorMessage = '';
  lastUpdated: Date | null = null;
  private refreshTimer?: ReturnType<typeof setInterval>;
  constructor(private investmentService: InvestmentService, private inquiryService: InvestmentInquiryService, private feedbackService: FeedbackService) {}
  ngOnInit(): void {
    this.refresh();
    this.refreshTimer = setInterval(() => this.refresh(), 10000);
  }
  ngOnDestroy(): void { if (this.refreshTimer) clearInterval(this.refreshTimer); }
  refresh(): void {
    if (this.refreshing) return;
    this.refreshing = true;
    this.errorMessage = '';
    let completed = 0;
    const complete = (failed = false) => {
      if (failed) this.errorMessage = 'Some dashboard data could not be loaded.';
      completed++;
      if (completed === 3) {
        this.loading = false;
        this.refreshing = false;
        this.lastUpdated = new Date();
      }
    };
    this.investmentService.getAllInvestments().subscribe({ next: data => { this.investments = data; complete(); }, error: () => complete(true) });
    this.inquiryService.getAllInquiries().subscribe({ next: data => { this.inquiries = data; complete(); }, error: () => complete(true) });
    this.feedbackService.getFeedbacks().subscribe({ next: data => { this.feedbacks = data; complete(); }, error: () => complete(true) });
  }
  count(status: string): number { return this.inquiries.filter(item => (item.status || 'PENDING') === status).length; }
  countPriority(priority: string): number { return this.inquiries.filter(item => item.priority === priority).length; }
  percentage(status: string): number { return this.inquiries.length ? Math.round(this.count(status) / this.inquiries.length * 100) : 0; }
  get activeInvestments(): number { return this.investments.filter(item => item.status?.toLowerCase() === 'active').length; }
  get openInquiries(): number { return this.inquiries.filter(item => (item.status || 'PENDING') !== 'RESOLVED').length; }
  performance(investment: Investment): number { return investment.purchasePrice ? Math.round(((investment.currentPrice - investment.purchasePrice) / investment.purchasePrice) * 1000) / 10 : 0; }
  priorityDescription(priority: string): string {
    return priority === 'High' ? 'Requires immediate attention' : priority === 'Medium' ? 'Needs follow-up' : 'Routine requests';
  }
}