/**
 * Shape of the aggregated Admin Console statistics returned by
 * GET /api/admin/dashboard. Every value is computed by the backend from
 * persisted records - nothing here is fabricated on the client.
 */

export interface PeriodCount {
  /** Time bucket: yyyy-MM (months) or yyyy-MM-dd (days). */
  period: string;
  count: number;
}

export interface DashboardInvestmentStats {
  total: number;
  active: number;
  /** Number of catalogue entries per asset type. */
  byType: { [type: string]: number };
  /** Investments recorded per purchase-date month, ascending. */
  activity: PeriodCount[];
}

export interface DashboardInquiryStats {
  total: number;
  /** Counts per stored status (NULL stored status counted as PENDING). */
  byStatus: { [status: string]: number };
  /** Counts per stored priority. */
  byPriority: { [priority: string]: number };
  /** Inquiries created per day, ascending. */
  trend: PeriodCount[];
}

export interface DashboardFeedbackStats {
  total: number;
  /** Counts per stored feedback category. */
  byCategory: { [category: string]: number };
}

export interface DashboardInquiryRow {
  investment: string;
  user: string;
  priority: string;
  status: string;
  date: string | null;
}

export interface DashboardInvestmentRow {
  name: string;
  type: string;
  assetClass?: string;
  market?: string;
  status: string;
  /** Value recorded in the catalogue - not live market data. */
  currentPrice?: number;
  currency?: string;
}

export interface DashboardFeedbackRow {
  category: string;
  date: string;
  text: string;
  user?: string;
}

export interface DashboardStats {
  investments: DashboardInvestmentStats;
  inquiries: DashboardInquiryStats;
  feedbacks: DashboardFeedbackStats;
  recentInquiries: DashboardInquiryRow[];
  recentInvestments: DashboardInvestmentRow[];
  recentFeedback: DashboardFeedbackRow[];
}
