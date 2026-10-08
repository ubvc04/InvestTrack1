import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from 'src/app/services/auth.service';
import { InvestmentInquiryService } from 'src/app/services/investment-inquiry.service';

@Component({
  selector: 'app-adminnav',
  templateUrl: './adminnav.component.html',
  styleUrls: ['./adminnav.component.css']
})
export class AdminnavComponent implements OnInit {

  username = '';
  userRole = '';

  showLogoutModal = false;

  openInquiries = 0;

  notifications: {
    inquiryId?: number;
    username: string;
    investmentName: string;
  }[] = [];

  constructor(
    public authService: AuthService,
    private router: Router,
    private inquiryService: InvestmentInquiryService
  ) { }

  ngOnInit(): void {

    this.username = this.authService.getUsername();
    this.userRole = this.authService.getUserRole();

    this.loadNotifications();

    setInterval(() => {

      this.loadNotifications();

    }, 5000);
  }

  loadNotifications(): void {

    this.inquiryService.getAllInquiries().subscribe({

      next: (inquiries) => {

        const pendingInquiries = inquiries.filter(
          inquiry =>
            (inquiry.status || 'PENDING') === 'PENDING'
        );

        const notificationsSeen =
        localStorage.getItem(
          'notificationsSeen'
        ) === 'true';
      
      if (notificationsSeen) {
      
        this.notifications = [];
      
        this.openInquiries = 0;
      
        return;
      }
      
      this.notifications = pendingInquiries.map(
        inquiry => ({
      
          inquiryId: inquiry.inquiryId,
      
          username:
            inquiry.user?.username || 'User',
      
          investmentName:
            inquiry.investment?.name || 'Investment'
        })
      );
      
      this.openInquiries =
        this.notifications.length;

        this.openInquiries =
          this.notifications.length;
      },

      error: error => {

        console.error(
          'Failed to load inquiry notifications',
          error
        );
      }
    });
  }

  goToInquiries(): void {

    this.notifications = [];
  
    this.openInquiries = 0;
  
    localStorage.setItem(
      'notificationsSeen',
      'true'
    );
  
    this.router.navigate([
      '/adminnav/view-inquiry'
    ]);
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