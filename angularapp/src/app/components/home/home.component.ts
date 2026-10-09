import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from 'src/app/services/auth.service';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit {

  username = '';
  userRole = '';
  isAdmin = false;

  aiSearch = '';

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {

    this.username = this.authService.getUsername();
    this.userRole = this.authService.getUserRole();
    this.isAdmin = this.authService.isAdmin();
  }

  runAiSearch(): void {

    const query = this.aiSearch.trim();

    if (!query) {
      return;
    }

    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }

    // Send the query to the investment page of the section the current user
    // belongs to. The /usernav routes only accept the "User" role, so an
    // Admin or SuperAdmin landing there would be bounced back to /login by
    // the auth guard.
    const target = this.isAdmin
      ? ['/adminnav/view-investment']
      : ['/usernav/view-investments'];

    this.router.navigate(
      target,
      {
        queryParams: {
          ai: query
        }
      }
    );
  }
}