import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Feedback } from 'src/app/models/feedback.model';
import { Investment } from 'src/app/models/investment.model';
import { AuthService } from 'src/app/services/auth.service';
import { FeedbackService } from 'src/app/services/feedback.service';
import { InvestmentService } from 'src/app/services/investment.service';

@Component({
  selector: 'app-user-add-feedback',
  templateUrl: './user-add-feedback.component.html',
  styleUrls: ['./user-add-feedback.component.css']
})
export class UserAddFeedbackComponent implements OnInit {
  investment: Investment | null = null;
  investmentFeedbackMode = false;
  feedbackType = 'Platform';
  feedbackText = '';
  subject = '';
  error = '';
  submitted = false;
  submitting = false;
  feedbackStatusLoading = false;
  feedbackStatusError = '';
  alreadySubmitted = false;

  constructor(
    private route: ActivatedRoute,
    private investmentService: InvestmentService,
    private feedbackService: FeedbackService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const investmentIdParam = this.route.snapshot.paramMap.get('investmentId');
    if (investmentIdParam === null) return;

    const investmentId = Number(investmentIdParam);
    if (!Number.isInteger(investmentId) || investmentId <= 0) {
      this.router.navigate(['/usernav/view-investments']);
      return;
    }

    this.investmentFeedbackMode = true;
    this.feedbackStatusLoading = true;
    this.investmentService.getInvestmentById(investmentId).subscribe({
      next: investment => this.investment = investment,
      error: () => this.router.navigate(['/error'])
    });
    this.loadInvestmentFeedbackStatus(investmentId);
  }

  onFeedbackTypeChange(type: string): void {
    this.feedbackType = type;
    if (type === 'Platform') this.subject = '';
  }

  private loadInvestmentFeedbackStatus(investmentId: number): void {
    this.feedbackStatusError = '';
    this.feedbackService.getAllFeedbacksByUserId(
      this.authService.getUserId()
    ).subscribe({
      next: feedbacks => {
        this.alreadySubmitted = feedbacks.some(feedback =>
          feedback.investment?.investmentId === investmentId &&
          feedback.category !== 'Platform' &&
          feedback.category !== 'Other'
        );
        this.feedbackStatusLoading = false;
      },
      error: () => {
        this.feedbackStatusError = 'We could not check whether you have already submitted feedback.';
        this.feedbackStatusLoading = false;
      }
    });
  }

  retryFeedbackStatus(): void {
    const investmentId = this.investment?.investmentId;
    if (investmentId) {
      this.feedbackStatusLoading = true;
      this.loadInvestmentFeedbackStatus(investmentId);
    }
  }

  submit(): void {
    if (this.submitting) return;
    this.error = '';
    const text = this.feedbackText.trim();
    const trimmedSubject = this.subject.trim();

    if (text.length < 10) {
      this.error = 'Enter at least 10 characters of feedback.';
      return;
    }
    if (this.investmentFeedbackMode) {
      if (!this.investment?.investmentId) {
        this.error = 'The selected investment could not be loaded.';
        return;
      }
      if (this.feedbackStatusLoading || this.feedbackStatusError) {
        this.error = this.feedbackStatusError || 'Please wait while we check your feedback status.';
        return;
      }
      if (this.alreadySubmitted) {
        this.error = 'You have already submitted feedback for this investment.';
        return;
      }
    } else if (this.feedbackType === 'Other' && !trimmedSubject) {
      this.error = 'Enter what this feedback is about.';
      return;
    }

    const feedback: Feedback = {
      feedbackText: text,
      category: this.investmentFeedbackMode ? 'Investment' : this.feedbackType,
      date: new Date().toISOString(),
      ...(this.investmentFeedbackMode && this.investment ? { investment: this.investment } : {}),
      ...(!this.investmentFeedbackMode && this.feedbackType === 'Other' ? { subject: trimmedSubject } : {})
    };

    this.submitting = true;
    this.feedbackService.sendFeedback(feedback).subscribe({
      next: () => {
        this.submitted = true;
        this.alreadySubmitted = this.investmentFeedbackMode;
        this.feedbackText = '';
        this.subject = '';
        this.submitting = false;
      },
      error: (response: HttpErrorResponse) => {
        this.error = response.error?.error ||
          'We could not submit your feedback. Please try again.';
        this.submitting = false;
      }
    });
  }
}