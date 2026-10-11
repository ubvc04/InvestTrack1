/**
 * Shared chart primitives for the Admin Console widgets.
 *
 * The palette is deliberately theme-agnostic: every colour stays legible on
 * both the light (#ffffff) and dark (#121b2c) card surfaces, so charts need no
 * per-theme recolouring.
 */

export const CHART_PALETTE = [
  '#2563eb', // blue (InvestTrack primary)
  '#24c8d9', // cyan
  '#10b981', // green
  '#f59e0b', // amber
  '#8b5cf6', // purple
  '#ef4444', // red
  '#0ea5e9', // sky
  '#64748b'  // slate
];

/** Status / priority accents shared by charts and badges. */
export const CHART_STATUS = {
  pending: '#f59e0b',
  resolved: '#10b981',
  default: '#2563eb',
  high: '#ef4444',
  medium: '#f59e0b',
  low: '#24c8d9',
  unknown: '#64748b'
};

export interface ChartSlice {
  label: string;
  value: number;
  color: string;
}

export interface BarDatum {
  label: string;
  value: number;
  color?: string;
}

export interface TrendPoint {
  period: string;
  count: number;
}
