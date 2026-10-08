import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Feedback } from 'src/app/models/feedback.model';
import { AuthService } from 'src/app/services/auth.service';
import { FeedbackService } from 'src/app/services/feedback.service';

@Component({
  selector: 'app-user-view-feedback',
  templateUrl: './user-view-feedback.component.html',
  styleUrls: ['./user-view-feedback.component.css']
})
export class UserViewFeedbackComponent implements OnInit {
  feedbacks: Feedback[] = [];
  loading = true;
  constructor(private feedbackService: FeedbackService, private authService: AuthService, private router: Router) { }
  ngOnInit(): void { this.feedbackService.getAllFeedbacksByUserId(this.authService.getUserId()).subscribe({ next: data =>
     { this.feedbacks = data; this.loading = false; }, 
     error: () => { this.loading = false; this.router.navigate(['/error']); }
     }); }
}
