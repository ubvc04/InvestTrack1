import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { AdminAddInvestmentComponent } from './components/admin-add-investment/admin-add-investment.component';
import { AdminConsoleComponent } from './components/admin-console/admin-console.component';
import { AdminEditInvestmentComponent } from './components/admin-edit-investment/admin-edit-investment.component';
import { AdminViewFeedbackComponent } from './components/admin-view-feedback/admin-view-feedback.component';
import { AdminViewInquiryComponent } from './components/admin-view-inquiry/admin-view-inquiry.component';
import { AdminViewInvestmentComponent } from './components/admin-view-investment/admin-view-investment.component';
import { AdminnavComponent } from './components/adminnav/adminnav.component';

import { ErrorComponent } from './components/error/error.component';
import { HomeComponent } from './components/home/home.component';
import { LoginComponent } from './components/login/login.component';
import { SignupComponent } from './components/signup/signup.component';

import { UserAddFeedbackComponent } from './components/user-add-feedback/user-add-feedback.component';
import { UserAddInquiryComponent } from './components/user-add-inquiry/user-add-inquiry.component';
import { UserViewFeedbackComponent } from './components/user-view-feedback/user-view-feedback.component';
import { UserViewInquiriesComponent } from './components/user-view-inquiries/user-view-inquiries.component';
import { UsernavComponent } from './components/usernav/usernav.component';
import { UserViewInvestmentComponent } from './components/user-view-investment/user-view-investment.component';

import { authGuard } from './guards/auth.guard';
import { ChangePasswordComponent } from './components/change-password/change-password.component';
import { ForgotPasswordComponent } from './components/forgot-password/forgot-password.component';
import { SuperAdminManagementComponent } from './components/super-admin-management/super-admin-management.component';

const routes: Routes = [
  
  // Default Route
  { path: '', redirectTo: 'login', pathMatch: 'full' },

  // Public Routes
  { path: 'login', component: LoginComponent },
  { path: 'signup', component: SignupComponent },
  { path: 'forgot-password', component: ForgotPasswordComponent },

  // Admin Routes
  {
    path: 'adminnav',
    component: AdminnavComponent,
    canActivate: [authGuard],
    data: { role: ['Admin', 'SuperAdmin'] },
    children: [
      { path: '', redirectTo: 'home', pathMatch: 'full' },
      { path: 'home', component: HomeComponent },
      { path: 'add-investment', component: AdminAddInvestmentComponent },
      { path: 'view-investment', component: AdminViewInvestmentComponent },
      { path: 'edit-investment/:id', component: AdminEditInvestmentComponent },
      { path: 'view-inquiry', component: AdminViewInquiryComponent },
      { path: 'view-feedback', component: AdminViewFeedbackComponent },
      { path: 'console', component: AdminConsoleComponent },
      { path: 'management', component: SuperAdminManagementComponent, canActivate: [authGuard], data: { role: 'SuperAdmin' } },
      { path: 'change-password', component: ChangePasswordComponent }
    ]
  },

  // User Routes
  {
    path: 'usernav',
    component: UsernavComponent,
    canActivate: [authGuard],
    data: { role: 'User' },
    children: [
      { path: '', redirectTo: 'home', pathMatch: 'full' },
      { path: 'home', component: HomeComponent },
      { path: 'view-investments', component: UserViewInvestmentComponent },
      { path: 'add-inquiry/:investmentId', component: UserAddInquiryComponent },
      { path: 'view-inquiries', component: UserViewInquiriesComponent },
      { path: 'add-feedback/:investmentId', component: UserAddFeedbackComponent },
      { path: 'add-feedback', component: UserAddFeedbackComponent },
      { path: 'view-feedback', component: UserViewFeedbackComponent },
      { path: 'change-password', component: ChangePasswordComponent }
    ]
  },

  // Error Route
  { path: 'error', component: ErrorComponent },

  // Wildcard Route
  { path: '**', redirectTo: 'login' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}