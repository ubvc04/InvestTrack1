import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from 'src/app/services/auth.service';

@Component({
  selector: 'app-usernav',
  templateUrl: './usernav.component.html',
  styleUrls: ['./usernav.component.css']
})
export class UsernavComponent implements OnInit {

  username = '';
  userRole = '';
  showLogoutModal = false;

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.username = this.authService.getUsername();
    this.userRole = this.authService.getUserRole();
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

  goToChangePassword(): void {
    this.router.navigate(['/usernav/change-password']);
  }

}