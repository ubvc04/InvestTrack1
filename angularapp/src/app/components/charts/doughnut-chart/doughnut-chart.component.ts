import { Component, Input } from '@angular/core';
import { ChartSlice } from '../chart-palette';

interface DoughnutSegment {
  slice: ChartSlice;
  /** Stroke length along the normalised 100-unit circumference. */
  length: number;
  /** Stroke offset (distance already travelled along the circumference). */
  offset: number;
}

/**
 * Dependency-free doughnut chart (SVG). Slices are drawn as stroke segments on
 * a normalised circle (circumference = 100), so no arc math is needed. All
 * colours are supplied by the caller; surfaces/legend text use theme tokens.
 */
@Component({
  selector: 'app-doughnut-chart',
  templateUrl: './doughnut-chart.component.html',
  styleUrls: ['./doughnut-chart.component.css']
})
export class DoughnutChartComponent {

  @Input() slices: ChartSlice[] = [];
  /** Small caption rendered inside the doughnut hole. */
  @Input() centerLabel = '';
  @Input() emptyMessage = 'No data available yet.';

  get total(): number {
    return this.slices.reduce((sum, slice) => sum + slice.value, 0);
  }

  percentage(value: number): number {
    return this.total ? Math.round((value / this.total) * 100) : 0;
  }

  segments(): DoughnutSegment[] {
    const circumference = 100;
    const result: DoughnutSegment[] = [];
    let travelled = 0;
    for (const slice of this.slices) {
      const length = this.total ? (slice.value / this.total) * circumference : 0;
      result.push({ slice, length, offset: travelled });
      travelled += length;
    }
    return result;
  }
}
