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

    this.router.navigate(
      ['/usernav/view-investments'],
      {
        queryParams: {
          ai: query
        }
      }
    );
  }
}