import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from 'src/app/services/auth.service';

@Component({
  selector: 'app-adminnav',
  templateUrl: './adminnav.component.html',
  styleUrls: ['./adminnav.component.css']
})
export class AdminnavComponent implements OnInit {

  username = '';
  userRole = '';

  showLogoutModal = false;

  constructor(
    public authService: AuthService,
    private router: Router
  ) { }

  ngOnInit(): void {

    this.username = this.authService.getUsername();
    this.userRole = this.authService.getUserRole();

    // Notification counts are handled by the shared <app-notification-bell> component,
    // which reads the real unread count from the backend (no inquiry polling anymore).
  }

  confirmLogout(): void {
    this.showLogoutModal = true;
  }

  onLogout(): void {

    this.showLogoutModal = false;

    this.authService.logout();

    this.router.navigate(['/login']);

  }

  cancelLogout(): void {
    this.showLogoutModal = false;
  }
}
