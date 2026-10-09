import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Notification } from 'src/app/models/notification.model';
import { AuthService } from 'src/app/services/auth.service';
import { NotificationService } from 'src/app/services/notification.service';

const POLL_INTERVAL_MS = 30000;

/**
 * Shared notification bell used in both navigation bars.
 *
 * The unread count comes from the backend (never from a hardcoded value), is capped at 99+ in
 * the badge, and is refreshed periodically so it cannot drift from the database. Opening the
 * dropdown loads the latest notifications, and mark-one / mark-all read actions are persisted
 * on the backend for the authenticated recipient only.
 */
@Component({
  selector: 'app-notification-bell',
  templateUrl: './notification-bell.component.html',
  styleUrls: ['./notification-bell.component.css']
})
export class NotificationBellComponent implements OnInit, OnDestroy {

  items: Notification[] = [];
  unreadCount = 0;
  loading = false;
  loaded = false;
  error = '';
  markingAll = false;

  private pollHandle: any = null;

  constructor(
    private notificationService: NotificationService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.refreshUnread();
    this.pollHandle = setInterval(() => this.refreshUnread(), POLL_INTERVAL_MS);
  }

  ngOnDestroy(): void {
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
  }

  get badgeLabel(): string {
    if (this.unreadCount === 0) {
      return 'Notifications, none unread';
    }
    return `Notifications, ${this.unreadCount > 99 ? '99 plus' : this.unreadCount} unread`;
  }

  get badgeText(): string {
    return this.unreadCount > 99 ? '99+' : String(this.unreadCount);
  }

  /** Refresh only the badge; cheap call used by the polling timer. */
  refreshUnread(): void {
    if (!this.authService.isLoggedIn()) {
      return;
    }
    this.notificationService.getUnreadCount().subscribe({
      next: count => this.unreadCount = count,
      error: () => { /* transient network failure: keep the previous count */ }
    });
  }

  /** Loads the newest page when the dropdown is opened. */
  onOpen(): void {
    this.load(0);
  }

  load(page = 0): void {
    this.loading = true;
    this.error = '';
    this.notificationService.getNotifications(page, 15).subscribe({
      next: list => {
        this.items = list.items;
        this.unreadCount = list.unreadCount;
        this.loading = false;
        this.loaded = true;
      },
      error: () => {
        this.error = 'Could not load notifications.';
        this.loading = false;
        this.loaded = true;
      }
    });
  }

  markAllRead(): void {
    if (this.markingAll || this.unreadCount === 0) {
      return;
    }
    this.markingAll = true;
    this.notificationService.markAllAsRead().subscribe({
      next: response => {
        this.unreadCount = response.unreadCount;
        this.items = this.items.map(item => ({ ...item, read: true }));
        this.markingAll = false;
      },
      error: () => {
        this.error = 'Could not update notifications.';
        this.markingAll = false;
      }
    });
  }

  /** Marks the opened notification as read and navigates to the related screen. */
  open(notification: Notification): void {
    if (!notification.read) {
      this.notificationService.markAsRead(notification.notificationId).subscribe({
        next: updated => {
          this.items = this.items.map(item =>
            item.notificationId === updated.notificationId ? { ...item, read: true } : item);
          this.unreadCount = Math.max(0, this.unreadCount - 1);
        },
        error: () => { /* navigation still proceeds */ }
      });
    }
    this.navigate(notification);
  }

  private navigate(notification: Notification): void {
    const isAdmin = this.authService.isAdmin() || this.authService.isSuperAdmin();
    if (notification.type === 'INQUIRY') {
      this.router.navigate([isAdmin ? '/adminnav/view-inquiry' : '/usernav/view-inquiries']);
    } else if (notification.type === 'FEEDBACK') {
      this.router.navigate([isAdmin ? '/adminnav/view-feedback' : '/usernav/view-feedback']);
    }
  }
}
