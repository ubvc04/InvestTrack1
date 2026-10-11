import { Component, Input, OnChanges } from '@angular/core';
import { BarDatum, CHART_PALETTE } from '../chart-palette';

/**
 * Dependency-free bar chart (CSS-based, fully theme-aware). Supports vertical
 * columns and horizontal rows; the horizontal mode keeps long category labels
 * readable. Value labels are rendered visibly and native tooltips are provided.
 */
@Component({
  selector: 'app-bar-chart',
  templateUrl: './bar-chart.component.html',
  styleUrls: ['./bar-chart.component.css']
})
export class BarChartComponent implements OnChanges {

  @Input() data: BarDatum[] = [];
  @Input() orientation: 'vertical' | 'horizontal' = 'vertical';
  @Input() emptyMessage = 'No data available yet.';

  max = 1;

  ngOnChanges(): void {
    this.max = Math.max(1, ...this.data.map(d => d.value));
  }

  colorOf(datum: BarDatum, index: number): string {
    return datum.color || CHART_PALETTE[index % CHART_PALETTE.length];
  }

  tooltip(datum: BarDatum): string {
    return `${datum.label}: ${datum.value}`;
  }
}
