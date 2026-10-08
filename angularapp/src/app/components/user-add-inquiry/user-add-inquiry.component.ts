import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { InvestmentInquiry } from 'src/app/models/investment-inquiry.model';
import { Investment } from 'src/app/models/investment.model';
import { AuthService } from 'src/app/services/auth.service';
import { InvestmentInquiryService } from 'src/app/services/investment-inquiry.service';
import { InvestmentService } from 'src/app/services/investment.service';

@Component({
  selector: 'app-user-add-inquiry',
  templateUrl: './user-add-inquiry.component.html',
  styleUrls: ['./user-add-inquiry.component.css']
})
export class UserAddInquiryComponent implements OnInit {

  investment: Investment | null = null;
  message = '';
  subject = '';
  priority = 'Medium';
  contactDetails = '';

  submitted = false;
  error = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private investmentService: InvestmentService,
    private inquiryService: InvestmentInquiryService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {

    const id = Number(
      this.route.snapshot.paramMap.get('investmentId')
    );

    if (!id) {

      this.router.navigate([
        '/usernav/view-investments'
      ]);

      return;
    }

    this.investmentService
      .getInvestmentById(id)
      .subscribe({

        next: investment => {

          this.investment = investment;
        },

        error: () => {

          this.router.navigate(['/error']);
        }
      });
  }

  submit(): void {

    this.error = '';

    if (
      !this.investment ||
      !this.message.trim()
    ) {

      this.error =
        'Please describe your question before submitting.';

      return;
    }

    const inquiry: InvestmentInquiry = {

      user: {
        userId: this.authService.getUserId(),
        username: this.authService.getUsername()
      } as any,

      investment: this.investment,

      message: this.message.trim(),
      subject: this.subject.trim() || this.investment.name,

      priority: this.priority,

      contactDetails: this.contactDetails.trim()
    };

    this.inquiryService
      .addInquiry(inquiry)
      .subscribe({

        next: () => {

          localStorage.setItem(
            'notificationsSeen',
            'false'
          );

          this.submitted = true;

          setTimeout(() => {

            this.router.navigate([
              '/usernav/view-inquiries'
            ]);

          }, 700);
        },

        error: () => {

          this.error =
            'We could not submit your inquiry. Please try again.';
        }
      });
  }
}