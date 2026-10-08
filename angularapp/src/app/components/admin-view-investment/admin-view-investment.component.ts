import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Investment } from 'src/app/models/investment.model';
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

  // AI search
  aiQuery = '';
  aiLoading = false;
  aiError = '';
  aiResultActive = false;

  constructor(
    private investmentService: InvestmentService,
    private aiService: AiService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadInvestments();
  }

  loadInvestments(): void {
    this.investmentService.getAllInvestments().subscribe({
      next: (data) => {
        this.investments = data;
        this.filteredInvestments = data;
        this.types = [...new Set(data.map(i => i.type))];
        this.aiResultActive = false;
        this.aiQuery = '';
      },
      error: () => this.router.navigate(['/error'])
    });
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

  // AI search
  onAiSearch(): void {
    if (!this.aiQuery.trim()) return;
    this.aiLoading = true;
    this.aiError = '';
    this.aiService.searchInvestments(this.aiQuery).subscribe({
      next: (results) => {
        this.filteredInvestments = results;
        this.aiResultActive = true;
        this.aiLoading = false;
      },
      error: (error) => {
        this.aiError = error?.error?.error
          || 'AI search failed. Please try again.';
        this.aiResultActive = false;
        this.aiLoading = false;
      }
    });
  }

  clearAiSearch(): void {
    this.aiQuery = '';
    this.aiResultActive = false;
    this.aiError = '';
    this.applyFilter();
  }
}