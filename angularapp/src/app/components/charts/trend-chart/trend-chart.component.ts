import { Component, Input, OnChanges } from '@angular/core';
import { TrendPoint } from '../chart-palette';

interface Coord {
  x: number;
  y: number;
  point: TrendPoint;
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/**
 * Dependency-free trend line chart (SVG), used for real historical series only
 * (investment activity per month, inquiries created per day). Renders an honest
 * empty state when no historical data exists - it never invents points.
 */
@Component({
  selector: 'app-trend-chart',
  templateUrl: './trend-chart.component.html',
  styleUrls: ['./trend-chart.component.css']
})
export class TrendChartComponent implements OnChanges {

  @Input() points: TrendPoint[] = [];
  /** 'day' (yyyy-MM-dd) or 'month' (yyyy-MM) bucket formatting for the x axis. */
  @Input() granularity: 'day' | 'month' = 'day';
  @Input() accent = '#2563eb';
  @Input() emptyMessage = 'No historical data yet.';

  readonly left = 12;
  readonly right = 292;
  readonly top = 10;
  readonly bottom = 84;

  coords: Coord[] = [];
  linePath = '';
  areaPath = '';
  yMax = 1;
  labelStep = 1;

  ngOnChanges(): void {
    this.coords = [];
    this.linePath = '';
    this.areaPath = '';
    if (!this.points.length) return;

    this.yMax = Math.max(1, ...this.points.map(p => p.count));
    const n = this.points.length;
    this.coords = this.points.map((point, i) => ({
      x: n === 1 ? (this.left + this.right) / 2
                 : this.left + (i * (this.right - this.left)) / (n - 1),
      y: this.bottom - (point.count / this.yMax) * (this.bottom - this.top),
      point
    }));
    this.linePath = this.coords.map(c => `${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(' ');
    const first = this.coords[0].x.toFixed(1);
    const last = this.coords[n - 1].x.toFixed(1);
    this.areaPath =
      `M ${first},${this.bottom} L ` +
      this.coords.map(c => `${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(' L ') +
      ` L ${last},${this.bottom} Z`;
    this.labelStep = Math.max(1, Math.ceil(n / 6));
  }

  showLabel(index: number): boolean {
    return index % this.labelStep === 0 || index === this.coords.length - 1;
  }

  /** Formats a bucket period as a short, readable x-axis label. */
  axisLabel(period: string): string {
    const parts = period.split('-');
    if (parts.length < 2) return period;
    const month = MONTHS[parseInt(parts[1], 10) - 1] || parts[1];
    if (this.granularity === 'month') {
      return `${month} '${parts[0].slice(2)}`;
    }
    return parts.length >= 3 ? `${month} ${parseInt(parts[2], 10)}` : month;
  }

  tooltip(coord: Coord): string {
    return `${coord.point.period}: ${coord.point.count}`;
  }

  get ariaLabel(): string {
    const first = this.points[0];
    const last = this.points[this.points.length - 1];
    return `Trend line chart, ${this.points.length} points from ${first.period} to ${last.period}`;
  }
}
