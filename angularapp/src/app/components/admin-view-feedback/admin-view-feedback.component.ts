import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Feedback } from 'src/app/models/feedback.model';
import { FeedbackService } from 'src/app/services/feedback.service';

@Component({
  selector: 'app-admin-view-feedback',
  templateUrl: './admin-view-feedback.component.html',
  styleUrls: ['./admin-view-feedback.component.css']
})
export class AdminViewFeedbackComponent implements OnInit {
  feedbacks: Feedback[] = [];
  filtered: Feedback[] = [];
  filterCategory = '';

  showProfileModal = false;
  showInvestmentModal = false;
  selectedFeedback: Feedback | null = null;

  // Administrator response workflow
  showRespondModal = false;
  respondText = '';
  responding = false;
  respondError = '';
  respondSuccess = '';

  constructor(private feedbackService: FeedbackService, private router: Router) {}

  ngOnInit(): void {
    this.feedbackService.getFeedbacks().subscribe({
      next: (data) => { this.feedbacks = data; this.filtered = data; },
      error: () => this.router.navigate(['/error'])
    });
  }

  applyFilter(): void {
    this.filtered = this.feedbacks.filter(f =>
      !this.filterCategory || f.category === this.filterCategory
    );
  }

  showProfile(fb: Feedback): void { this.selectedFeedback = fb; this.showProfileModal = true; }
  showInvestment(fb: Feedback): void { this.selectedFeedback = fb; this.showInvestmentModal = true; }
  closeModals(): void { this.showProfileModal = false; this.showInvestmentModal = false; }

  openRespond(fb: Feedback): void {
    this.selectedFeedback = fb;
    this.respondText = fb.adminResponse || '';
    this.respondError = '';
    this.respondSuccess = '';
    this.responding = false;
    this.showRespondModal = true;
  }

  closeRespond(): void {
    if (this.responding) return;
    this.showRespondModal = false;
    this.respondError = '';
    this.respondSuccess = '';
  }

  submitRespond(): void {
    const fb = this.selectedFeedback;
    if (!fb?.feedbackId || this.responding) return;

    const text = this.respondText.trim();
    if (!text) {
      this.respondError = 'A response is required.';
      return;
    }

    this.responding = true;
    this.respondError = '';
    this.respondSuccess = '';

    this.feedbackService.respondToFeedback(fb.feedbackId, text).subscribe({
      next: (updated) => {
        const index = this.feedbacks.findIndex(f => f.feedbackId === updated.feedbackId);
        if (index >= 0) {
          this.feedbacks[index] = updated;
        }
        this.applyFilter();
        this.responding = false;
        this.respondSuccess = 'Response sent — the user has been notified.';
      },
      error: (error) => {
        this.responding = false;
        this.respondError = this.extractError(error);
      }
    });
  }

  private extractError(error: any): string {
    const body = error?.error;
    if (typeof body === 'string' && body.trim()) return body.trim();
    if (typeof body?.message === 'string' && body.message.trim()) return body.message.trim();
    if (error?.status === 0) return 'Cannot reach the server. Please try again.';
    return 'Could not send the response. Please try again.';
  }
}
