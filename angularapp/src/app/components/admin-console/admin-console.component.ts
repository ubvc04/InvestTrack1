import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { DashboardStats, DashboardInvestmentRow } from 'src/app/models/dashboard-stats.model';
import { DashboardService } from 'src/app/services/dashboard.service';
import { BarDatum, CHART_PALETTE, CHART_STATUS, ChartSlice, TrendPoint } from '../charts/chart-palette';

/** Background poll interval (only while the tab is visible). */
const REFRESH_INTERVAL_MS = 60000;
/** Minimum age of the last fetch before a window-focus re-fetch triggers. */
const FOCUS_REVALIDATE_MS = 30000;

/**
 * Data-driven Admin Console.
 *
 * Every displayed number, chart and table row comes from the single backend
 * aggregation endpoint (GET /api/admin/dashboard), which computes all values
 * from persisted database records. Nothing is hardcoded or fabricated here;
 * on refresh failure the last successfully loaded data is preserved.
 */
@Component({
  selector: 'app-admin-console',
  templateUrl: './admin-console.component.html',
  styleUrls: ['./admin-console.component.css']
})
export class AdminConsoleComponent implements OnDestroy, OnInit {

  stats: DashboardStats | null = null;
  loading = true;
  refreshing = false;
  errorMessage = '';
  lastUpdated: Date | null = null;

  private refreshTimer?: ReturnType<typeof setInterval>;
  private lastFetchAt = 0;

  constructor(private dashboardService: DashboardService) {}

  ngOnInit(): void {
    this.refresh();
    this.refreshTimer = setInterval(() => {
      if (!document.hidden) this.refresh();
    }, REFRESH_INTERVAL_MS);
  }

  ngOnDestroy(): void {
    if (this.refreshTimer) clearInterval(this.refreshTimer);
  }

  /** Revalidate when the tab regains focus, without hammering the API. */
  @HostListener('window:focus')
  onWindowFocus(): void {
    if (Date.now() - this.lastFetchAt >= FOCUS_REVALIDATE_MS) this.refresh();
  }

  refresh(): void {
    if (this.refreshing) return;
    this.refreshing = true;
    this.lastFetchAt = Date.now();
    this.dashboardService.getDashboardStats().subscribe({
      next: (stats) => {
        this.stats = stats;
        this.loading = false;
        this.refreshing = false;
        this.errorMessage = '';
        this.lastUpdated = new Date();
      },
      error: () => {
        this.loading = false;
        this.refreshing = false;
        // Never replace real data with fake values - keep the last good load.
        this.errorMessage = this.stats
          ? 'Dashboard data could not be refreshed — showing the last loaded values.'
          : 'Dashboard data could not be loaded. Check your connection and retry.';
      }
    });
  }

  // ------------------------------------------------- KPI accessors (backend values)

  get catalogTotal(): number { return this.stats?.investments.total ?? 0; }
  get activeInvestments(): number { return this.stats?.investments.active ?? 0; }
  get feedbackTotal(): number { return this.stats?.feedbacks.total ?? 0; }
  get totalInquiries(): number { return this.stats?.inquiries.total ?? 0; }
  get resolvedInquiries(): number { return this.stats?.inquiries.byStatus['RESOLVED'] ?? 0; }
  get pendingInquiries(): number { return this.stats?.inquiries.byStatus['PENDING'] ?? 0; }
  get openInquiries(): number { return this.totalInquiries - this.resolvedInquiries; }
  get highPriority(): number { return this.stats?.inquiries.byPriority['High'] ?? 0; }

  // ------------------------------------------------- Chart datasets (derived from the same aggregates)

  /** Doughnut: catalogue distribution by asset type. */
  get typeSlices(): ChartSlice[] {
    const byType = this.stats?.investments.byType ?? {};
    return Object.keys(byType).map((label, index) => ({
      label,
      value: byType[label],
      color: CHART_PALETTE[index % CHART_PALETTE.length]
    }));
  }

  /** Bar: investment count per asset type (same data, comparison view). */
  get typeBars(): BarDatum[] {
    return this.typeSlices.map(slice => ({ label: slice.label, value: slice.value, color: slice.color }));
  }

  /** Line: investment activity per recorded purchase month (last 12 buckets). */
  get activityPoints(): TrendPoint[] {
    return (this.stats?.investments.activity ?? []).slice(-12).map(b => ({ period: b.period, count: b.count }));
  }

  /** Doughnut: inquiry status distribution (PENDING / RESOLVED / others). */
  get statusSlices(): ChartSlice[] {
    const byStatus = this.stats?.inquiries.byStatus ?? {};
    const order = ['PENDING', 'RESOLVED'];
    return Object.keys(byStatus)
      .sort((a, b) => this.indexOfOrLast(a, order) - this.indexOfOrLast(b, order))
      .map(status => ({ label: this.titleCase(status), value: byStatus[status], color: this.statusColor(status) }));
  }

  /** Horizontal bars: inquiry priority distribution (High / Medium / Low / others). */
  get priorityBars(): BarDatum[] {
    const byPriority = this.stats?.inquiries.byPriority ?? {};
    const order = ['High', 'Medium', 'Low'];
    return Object.keys(byPriority)
      .sort((a, b) => this.indexOfOrLast(a, order) - this.indexOfOrLast(b, order))
      .map(priority => ({ label: priority, value: byPriority[priority], color: this.priorityColor(priority) }));
  }

  /** Line: inquiries created per day (last 30 recorded days). */
  get inquiryTrendPoints(): TrendPoint[] {
    return (this.stats?.inquiries.trend ?? []).slice(-30).map(b => ({ period: b.period, count: b.count }));
  }

  // ------------------------------------------------- Presentation helpers

  titleCase(value: string): string {
    return value ? value.charAt(0) + value.slice(1).toLowerCase() : '';
  }

  statusColor(status: string): string {
    if (status === 'PENDING') return CHART_STATUS.pending;
    if (status === 'RESOLVED') return CHART_STATUS.resolved;
    return CHART_STATUS.default;
  }

  priorityColor(priority: string): string {
    if (priority === 'High') return CHART_STATUS.high;
    if (priority === 'Medium') return CHART_STATUS.medium;
    if (priority === 'Low') return CHART_STATUS.low;
    return CHART_STATUS.unknown;
  }

  statusPillClass(status: string): string {
    return status === 'RESOLVED' ? 'pill-success'
         : status === 'PENDING' ? 'pill-warning'
         : 'pill-info';
  }

  priorityPillClass(priority: string): string {
    return priority === 'High' ? 'pill-danger'
         : priority === 'Medium' ? 'pill-warning'
         : priority === 'Low' ? 'pill-info'
         : 'pill-muted';
  }

  /** Recorded catalogue value - never presented as live market data. */
  formatPrice(row: DashboardInvestmentRow): string {
    if (row.currentPrice == null) return '—';
    const value = row.currentPrice.toLocaleString('en-US', {
      minimumFractionDigits: 0, maximumFractionDigits: 2
    });
    return row.currency ? `${row.currency} ${value}` : value;
  }

  catalogType(row: DashboardInvestmentRow): string {
    return row.assetClass || row.type;
  }

  private indexOfOrLast(value: string, order: string[]): number {
    const index = order.indexOf(value);
    return index === -1 ? order.length : index;
  }
}
