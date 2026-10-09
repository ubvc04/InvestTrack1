import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Investment } from 'src/app/models/investment.model';
import { AiSearchResponse, AiSearchResult } from 'src/app/models/ai-search.model';
import { InvestmentService } from 'src/app/services/investment.service';
import { AiService } from 'src/app/services/ai.service';
@Component({
  selector: 'app-admin-view-investment',
  templateUrl: './admin-view-investment.component.html',
  styleUrls: ['./admin-view-investment.component.css']
})
export class AdminViewInvestmentComponent implements OnInit {
  investments: Investment[] = [];
  filteredInvestments: Investment[] = [];
  searchName = '';
  filterType = '';
  types: string[] = [];

  // Delete modal
  showDeleteModal = false;
  selectedId: number | null = null;

  // AI search — completely separate state from the normal investment list.
  // The normal list (investments / filteredInvestments) is never overwritten by AI results.
  aiQuery = '';
  aiLoading = false;
  aiError = '';
  aiResultActive = false;
  aiResponse: AiSearchResponse | null = null;
  aiResults: AiSearchResult[] = [];
  private openAiDetails = new Set<number>();

  // AI query arriving through the URL (?ai=...), applied once investments load
  private pendingAiQuery = '';

  constructor(
    private investmentService: InvestmentService,
    private aiService: AiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      const aiQuery = params['ai'];
      if (aiQuery) {
        this.pendingAiQuery = aiQuery;
        this.applyPendingAiSearch();
      }
    });
    this.loadInvestments();
  }

  loadInvestments(): void {
    this.investmentService.getAllInvestments().subscribe({
      next: (data) => {
        this.investments = data;
        this.filteredInvestments = data;
        this.types = [...new Set(data.map(i => i.type))];
        this.resetAiState();
        this.applyPendingAiSearch();
      },
      error: () => this.router.navigate(['/error'])
    });
  }

  private applyPendingAiSearch(): void {
    if (!this.pendingAiQuery || this.investments.length === 0) {
      return;
    }
    this.aiQuery = this.pendingAiQuery;
    this.pendingAiQuery = '';
    this.onAiSearch();
  }

  applyFilter(): void {
    this.filteredInvestments = this.investments.filter(inv => {
      const searchText = `${inv.name} ${inv.symbol ?? ''} ${inv.exchange ?? ''} ${inv.market ?? ''}`.toLowerCase();
      const nameMatch = searchText.includes(this.searchName.toLowerCase());
      const typeMatch = !this.filterType || inv.type === this.filterType;
      return nameMatch && typeMatch;
    });
  }

  editInvestment(id: number): void {
    this.router.navigate(['/adminnav/edit-investment', id]);
  }

  confirmDelete(id: number): void {
    this.selectedId = id;
    this.showDeleteModal = true;
  }

  onDelete(): void {
    if (this.selectedId === null) return;
    this.investmentService.deleteInvestment(this.selectedId).subscribe({
      next: () => { this.showDeleteModal = false; this.selectedId = null; this.loadInvestments(); },
      error: () => { this.showDeleteModal = false; }
    });
  }

  cancelDelete(): void {
    this.showDeleteModal = false;
    this.selectedId = null;
  }

  // ---------------------------------------------------------------------
  // AI search
  // ---------------------------------------------------------------------

  /**
   * Sends the raw, unmodified query to the backend AI search. Every response is a fresh
   * ranking (scores are recalculated per query, never cached or reused), and results live in
   * their own state so the normal investment list and its filters stay intact.
   */
  onAiSearch(): void {
    const query = this.aiQuery.trim();
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

  private resetAiState(): void {
    this.aiResultActive = false;
    this.aiResponse = null;
    this.aiResults = [];
    this.aiError = '';
    this.aiQuery = '';
    this.openAiDetails.clear();
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
}
