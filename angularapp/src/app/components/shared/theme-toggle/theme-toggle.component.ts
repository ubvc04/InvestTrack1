import { Component } from '@angular/core';
import { ThemeService } from 'src/app/services/theme.service';

/**
 * Light/dark theme switch used in both navigation bars. All state lives in ThemeService so the
 * selected theme stays consistent across routes and after a reload.
 */
@Component({
  selector: 'app-theme-toggle',
  templateUrl: './theme-toggle.component.html',
  styleUrls: ['./theme-toggle.component.css']
})
export class ThemeToggleComponent {

  constructor(public themeService: ThemeService) {}

  get isDark(): boolean {
    return this.themeService.isDark;
  }

  get label(): string {
    return this.isDark ? 'Switch to light theme' : 'Switch to dark theme';
  }

  toggle(): void {
    this.themeService.toggle();
  }
}
