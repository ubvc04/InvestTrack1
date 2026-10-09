import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Feedback } from 'src/app/models/feedback.model';
import { Investment } from 'src/app/models/investment.model';
import { AiSearchResponse, AiSearchResult } from 'src/app/models/ai-search.model';
import { AiService } from 'src/app/services/ai.service';
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

  // AI search — completely separate state from the normal investment list.
  // The normal list (investments / filtered) is never overwritten by AI results.
  aiLoading = false;
  aiError = '';
  aiResultActive = false;
  aiResponse: AiSearchResponse | null = null;
  aiResults: AiSearchResult[] = [];
  private openAiDetails = new Set<number>();

  feedbackStatusLoading = true;
  feedbackStatusError = '';

  investmentsWithFeedback = new Set<number>();

  constructor(
    private investmentService: InvestmentService,
    private feedbackService: FeedbackService,
    private authService: AuthService,
    private aiService: AiService,
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

  /**
   * Sends the raw, unmodified query to the backend AI search. Scores are recalculated for
   * every query (never cached or reused) and the results are kept in their own state so the
   * normal investment list, filters and empty states stay intact.
   */
  runAiSearch(): void {

    const query = this.aiSearch.trim();

    if (!query) {
      this.aiError = 'Enter an investment question to run the AI search.';
      return;
    }

    this.aiLoading = true;
    this.aiError = '';
    this.aiResponse = null;
    this.aiResults = [];
    this.openAiDetails.clear();

    this.aiService.searchDetailed(query).subscribe({
      next: (response) => {
        this.aiResponse = response;
        this.aiResults = response.results || [];
        this.aiResultActive = true;
        this.aiLoading = false;
        this.applyFilter();
      },
      error: (error) => {
        this.aiError = this.extractAiError(error);
        this.aiResponse = null;
        this.aiResults = [];
        this.aiResultActive = false;
        this.aiLoading = false;
      }
    });
  }

  /** Returns to the normal list without touching the entered query or the filters. */
  clearAiSearch(): void {
    this.aiResultActive = false;
    this.aiResponse = null;
    this.aiResults = [];
    this.aiError = '';
    this.openAiDetails.clear();
    this.applyFilter();
  }

  /** Server messages (e.g. an unrelated query) are plain text bodies. */
  private extractAiError(error: any): string {
    const body = error?.error;
    if (typeof body === 'string' && body.trim()) {
      return body.trim();
    }
    if (typeof body?.message === 'string' && body.message.trim()) {
      return body.message.trim();
    }
    if (typeof body?.error === 'string' && body.error.trim()) {
      return body.error.trim();
    }
    if (error?.status === 0) {
      return 'Cannot reach the AI service. Please try again in a moment.';
    }
    return 'AI search failed. Please try again.';
  }

  toggleAiDetails(investmentId: number | null): void {
    if (investmentId === null) return;
    if (this.openAiDetails.has(investmentId)) {
      this.openAiDetails.delete(investmentId);
    } else {
      this.openAiDetails.add(investmentId);
    }
  }

  isAiDetailsOpen(investmentId: number | null): boolean {
    return investmentId !== null && this.openAiDetails.has(investmentId);
  }

  /** Human readable state label for a single criterion. */
  criterionStateLabel(state: string): string {
    switch (state) {
      case 'MATCH': return 'Match';
      case 'MISMATCH': return 'Does not match';
      case 'UNVERIFIABLE': return 'Not verified';
      default: return state;
    }
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
