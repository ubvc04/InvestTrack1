import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { AdminAddInvestmentComponent } from './components/admin-add-investment/admin-add-investment.component';
import { AdminConsoleComponent } from './components/admin-console/admin-console.component';
import { AdminEditInvestmentComponent } from './components/admin-edit-investment/admin-edit-investment.component';
import { AdminViewFeedbackComponent } from './components/admin-view-feedback/admin-view-feedback.component';
import { AdminViewInquiryComponent } from './components/admin-view-inquiry/admin-view-inquiry.component';
import { AdminViewInvestmentComponent } from './components/admin-view-investment/admin-view-investment.component';
import { AdminnavComponent } from './components/adminnav/adminnav.component';
import { AuthguardComponent } from './components/authguard/authguard.component';
import { ErrorComponent } from './components/error/error.component';
//import { HomePageComponent } from './components/home-page/home-page.component';
import { LoginComponent } from './components/login/login.component';
import { SignupComponent } from './components/signup/signup.component';
import { UserAddFeedbackComponent } from './components/user-add-feedback/user-add-feedback.component';
import { UserAddInquiryComponent } from './components/user-add-inquiry/user-add-inquiry.component';
import { UserViewFeedbackComponent } from './components/user-view-feedback/user-view-feedback.component';
//import { UserViewInquiryComponent } from './components/user-view-inquiry/user-view-inquiry.component';
import { UserViewInvestmentComponent } from './components/user-view-investment/user-view-investment.component';
import { UsernavComponent } from './components/usernav/usernav.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { HttpClientModule } from '@angular/common/http';
import { UserViewInquiriesComponent } from './components/user-view-inquiries/user-view-inquiries.component';
import { HomeComponent } from './components/home/home.component';
import { ChangePasswordComponent } from './components/change-password/change-password.component';
import { ForgotPasswordComponent } from './components/forgot-password/forgot-password.component';
import { SuperAdminManagementComponent } from './components/super-admin-management/super-admin-management.component';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { AuthInterceptor } from './interceptors/auth.interceptor';
import { ThemeToggleComponent } from './components/shared/theme-toggle/theme-toggle.component';
import { NotificationBellComponent } from './components/shared/notification-bell/notification-bell.component';

@NgModule({
  declarations: [
    AppComponent,
    AdminAddInvestmentComponent,
    AdminConsoleComponent,
    AdminEditInvestmentComponent,
    AdminViewFeedbackComponent,
    AdminViewInquiryComponent,
    AdminViewInvestmentComponent,
    AdminnavComponent,
    AuthguardComponent,
    ErrorComponent,
    
    LoginComponent,
    SignupComponent,
    UserAddFeedbackComponent,
    UserAddInquiryComponent,
    UserViewFeedbackComponent,
    UserViewInvestmentComponent,
    UsernavComponent,
    UserViewInquiriesComponent,
    HomeComponent,
    ChangePasswordComponent,
    ForgotPasswordComponent,
    SuperAdminManagementComponent,
    ThemeToggleComponent,
    NotificationBellComponent,
  ],


  imports: [
    BrowserModule,
    AppRoutingModule,
    FormsModule,
    ReactiveFormsModule,
    HttpClientModule
  ],
  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
