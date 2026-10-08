import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Feedback } from 'src/app/models/feedback.model';
import { Investment } from 'src/app/models/investment.model';
import { AuthService } from 'src/app/services/auth.service';
import { FeedbackService } from 'src/app/services/feedback.service';
import { InvestmentService } from 'src/app/services/investment.service';

@Component({
  selector: 'app-user-view-investment',
  templateUrl: './user-view-investment.component.html',
  styleUrls: ['./user-view-investment.component.css']
})
export class UserViewInvestmentComponent implements OnInit {

  investments: Investment[] = [];
  filtered: Investment[] = [];

  search = '';
  aiSearch = '';
  type = '';

  loading = true;

  feedbackStatusLoading = true;
  feedbackStatusError = '';

  investmentsWithFeedback = new Set<number>();

  constructor(
    private investmentService: InvestmentService,
    private feedbackService: FeedbackService,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {

    this.investmentService.getAllInvestments().subscribe({
      next: (investments) => {

        this.investments = investments;
        this.filtered = investments;

        this.route.queryParams.subscribe(params => {

          const aiQuery = params['ai'];

          if (aiQuery) {

            this.aiSearch = aiQuery;

            setTimeout(() => {
              this.runAiSearch();
            });
          }
        });

        this.loading = false;
      },

      error: () => {

        this.loading = false;

        this.router.navigate(['/error']);
      }
    });

    this.loadFeedbackStatus();
  }

  private loadFeedbackStatus(): void {

    this.feedbackStatusError = '';

    this.feedbackService
      .getAllFeedbacksByUserId(this.authService.getUserId())
      .subscribe({

        next: (feedbacks: Feedback[]) => {

          this.investmentsWithFeedback = new Set(
            feedbacks
              .filter(
                feedback =>
                  feedback.investment &&
                  feedback.category !== 'Platform' &&
                  feedback.category !== 'Other'
              )
              .map(
                feedback => feedback.investment!.investmentId
              )
              .filter(
                (id): id is number => typeof id === 'number'
              )
          );

          this.feedbackStatusLoading = false;
        },

        error: () => {

          this.feedbackStatusError =
            'We could not check your investment feedback status.';

          this.feedbackStatusLoading = false;
        }
      });
  }

  retryFeedbackStatus(): void {

    this.feedbackStatusLoading = true;

    this.loadFeedbackStatus();
  }

  applyFilter(): void {

    const query = this.search.trim().toLowerCase();

    this.filtered = this.investments.filter(
      investment =>

        (
          !query ||

          `${investment.name}
          ${investment.symbol ?? ''}
          ${investment.exchange ?? ''}
          ${investment.market ?? ''}
          ${investment.description}
          ${investment.type}`
            .toLowerCase()
            .includes(query)

        ) &&

        (
          !this.type ||
          investment.type === this.type
        )
    );
  }

  runAiSearch(): void {

    const query = this.aiSearch.trim().toLowerCase();

    if (!query) {

      this.filtered = [...this.investments];
      return;
    }

    this.filtered = this.investments.filter(
      investment =>

        `${investment.name}
        ${investment.symbol ?? ''}
        ${investment.exchange ?? ''}
        ${investment.market ?? ''}
        ${investment.description}
        ${investment.type}`
          .toLowerCase()
          .includes(query)
    );
  }

  get types(): string[] {

    return [
      ...new Set(
        this.investments
          .map(investment => investment.type)
          .filter(Boolean)
      )
    ];
  }

  inquire(investment: Investment): void {

    if (investment.investmentId) {

      this.router.navigate([
        '/usernav/add-inquiry',
        investment.investmentId
      ]);
    }
  }

  giveFeedback(investment: Investment): void {

    if (investment.investmentId) {

      this.router.navigate([
        '/usernav/add-feedback',
        investment.investmentId
      ]);
    }
  }

  hasSubmittedFeedback(investment: Investment): boolean {

    return !!investment.investmentId &&
      this.investmentsWithFeedback.has(
        investment.investmentId
      );
  }
}