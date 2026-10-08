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
}
