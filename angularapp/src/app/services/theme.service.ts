import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'investtrack-theme';

/**
 * Central light/dark theme state for the whole application.
 *
 * The selected theme is stored in localStorage, restored on startup and applied through the
 * `data-theme` attribute on `<html>`, which every stylesheet reads through CSS variables.
 * With no stored preference the operating system preference is used and followed live.
 */
@Injectable({
  providedIn: 'root'
})
export class ThemeService implements OnDestroy {

  private readonly themeSubject = new BehaviorSubject<ThemeMode>(this.readStoredTheme());
  readonly theme$ = this.themeSubject.asObservable();

  private readonly mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
  private readonly mediaListener = (event: MediaQueryListEvent) => {
    if (!localStorage.getItem(STORAGE_KEY)) {
      this.apply(event.matches ? 'dark' : 'light');
    }
  };

  constructor() {
    this.apply(this.themeSubject.value);
    this.mediaQuery.addEventListener('change', this.mediaListener);
  }

  get theme(): ThemeMode {
    return this.themeSubject.value;
  }

  get isDark(): boolean {
    return this.themeSubject.value === 'dark';
  }

  setTheme(mode: ThemeMode): void {
    localStorage.setItem(STORAGE_KEY, mode);
    this.apply(mode);
    this.themeSubject.next(mode);
  }

  toggle(): void {
    this.setTheme(this.themeSubject.value === 'dark' ? 'light' : 'dark');
  }

  ngOnDestroy(): void {
    this.mediaQuery.removeEventListener('change', this.mediaListener);
  }

  private apply(mode: ThemeMode): void {
    document.documentElement.setAttribute('data-theme', mode);
  }

  private readStoredTheme(): ThemeMode {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'light' || stored === 'dark') {
      return stored;
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
}
