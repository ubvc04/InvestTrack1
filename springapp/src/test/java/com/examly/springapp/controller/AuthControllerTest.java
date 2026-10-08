package com.examly.springapp.controller;

import com.examly.springapp.config.JwtUtils;
import com.examly.springapp.dto.LoginResponseDTO;
import com.examly.springapp.dto.UserRequestDTO;
import com.examly.springapp.dto.UserResponseDTO;
import com.examly.springapp.model.User;
import com.examly.springapp.service.EmailService;
import com.examly.springapp.service.OtpService;
import com.examly.springapp.service.PasswordRecoveryService;
import com.examly.springapp.service.TwilioVerificationService;
import com.examly.springapp.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the REST boundaries of registration, admin creation and OTP recovery:
 * phone verification is enforced by the backend, OTP authentication reuses the
 * existing JWT machinery and no OTP is ever echoed back to the client.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtUtils jwtUtil;

    @Mock
    private OtpService otpService;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordRecoveryService passwordRecoveryService;

    @Mock
    private TwilioVerificationService twilioVerificationService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(userService, jwtUtil, otpService,
                emailService, passwordRecoveryService, twilioVerificationService);
    }

    private UserRequestDTO request(String role) {
        return new UserRequestDTO("new user", "new@user.com", "Secret@123",
                "9876543210", role);
    }

    @Test
    void registrationIsRejectedWithoutPhoneVerification() {
        when(otpService.isVerified("new@user.com")).thenReturn(true);
        when(twilioVerificationService.consumeVerifiedPhone("9876543210")).thenReturn(false);

        assertThatThrownBy(() -> authController.register(request("User")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("phone number");

        verify(userService, never()).createUser(any());
    }

    @Test
    void registrationIsRejectedWithoutEmailVerification() {
        when(otpService.isVerified("new@user.com")).thenReturn(false);

        assertThatThrownBy(() -> authController.register(request("User")))
                .isInstanceOf(ResponseStatusException.class);

        verify(twilioVerificationService, never()).consumeVerifiedPhone(anyString());
        verify(userService, never()).createUser(any());
    }

    @Test
    void registrationSucceedsAfterBothVerifications() {
        User created = new User(11L, "new user", "new@user.com", "encoded",
                "9876543210", "User");
        when(otpService.isVerified("new@user.com")).thenReturn(true);
        when(twilioVerificationService.consumeVerifiedPhone("9876543210")).thenReturn(true);
        when(userService.createUser(any(User.class))).thenReturn(created);

        ResponseEntity<UserResponseDTO> response = authController.register(request("User"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userRole()).isEqualTo("User");
        // the JPA entity (and its encoded password) never crosses the API boundary
        assertThat(response.getBody().userId()).isEqualTo(11L);
        assertThat(response.getBody().mustChangePassword()).isFalse();
        verify(userService).createUser(any(User.class));
    }

    @Test
    void adminCreationIsRejectedWithoutPhoneVerification() {
        when(otpService.isVerified("new@user.com")).thenReturn(true);
        when(twilioVerificationService.consumeVerifiedPhone("9876543210")).thenReturn(false);
        when(userService.mustChangePassword("super@admin.com")).thenReturn(false);

        assertThatThrownBy(() -> authController.createAdmin(request("Admin"), superAdmin()))
                .isInstanceOf(ResponseStatusException.class);

        verify(userService, never()).createAdmin(any());
    }

    @Test
    void adminCreationSucceedsAfterPhoneVerification() {
        User created = new User(12L, "new admin", "new@user.com", "encoded",
                "9876543210", "Admin");
        when(otpService.isVerified("new@user.com")).thenReturn(true);
        when(twilioVerificationService.consumeVerifiedPhone("9876543210")).thenReturn(true);
        when(userService.mustChangePassword("super@admin.com")).thenReturn(false);
        when(userService.createAdmin(any(User.class))).thenReturn(created);

        ResponseEntity<UserResponseDTO> response = authController.createAdmin(request("Admin"), superAdmin());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userRole()).isEqualTo("Admin");
        verify(userService).createAdmin(any(User.class));
    }

    @Test
    void adminCreationIsBlockedUntilTheInitialPasswordIsChanged() {
        when(userService.mustChangePassword("super@admin.com")).thenReturn(true);

        assertThatThrownBy(() -> authController.createAdmin(request("Admin"), superAdmin()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(userService, never()).createAdmin(any());
    }

    @Test
    void adminCreationIsRestrictedToSuperAdmin() throws Exception {
        Method createAdmin = AuthController.class.getMethod("createAdmin",
                UserRequestDTO.class, org.springframework.security.core.Authentication.class);
        Method getAdmins = AuthController.class.getMethod("getAdmins",
                org.springframework.security.core.Authentication.class);

        assertThat(createAdmin.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(getAdmins.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(createAdmin.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('SuperAdmin')");
        assertThat(getAdmins.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('SuperAdmin')");
    }

    private org.springframework.security.core.Authentication superAdmin() {
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated("super@admin.com", null, java.util.List.of());
    }

    @Test
    void forgotPasswordOtpResponseNeverContainsTheOtp() {
        ResponseEntity<Map<String, String>> response =
                authController.sendForgotPasswordOtp(
                        new com.examly.springapp.dto.ForgotPasswordRequestDTO("user@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsOnlyKeys("message");
        verify(passwordRecoveryService).sendOtp("user@example.com");
        verify(emailService, never()).sendPasswordRecoveryOtpEmail(anyString(), anyString());
    }

    @Test
    void verifiedForgotPasswordOtpIssuesJwtAndForcesPasswordChange() {
        User user = new User(5L, "user", "user@example.com", "encoded",
                "9876543210", "User");
        user.setMustChangePassword(true);
        when(passwordRecoveryService.verifyOtp("user@example.com", "123456")).thenReturn(user);
        when(jwtUtil.generateToken("user@example.com", "User", 5L)).thenReturn("jwt-token");

        ResponseEntity<LoginResponseDTO> response =
                authController.verifyForgotPasswordOtp(
                        new com.examly.springapp.dto.VerifyForgotPasswordOtpDTO("user@example.com", "123456"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponseDTO body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.token()).isEqualTo("jwt-token");
        assertThat(body.userRole()).isEqualTo("User");
        assertThat(body.mustChangePassword()).isTrue();
    }

    @Test
    void recoveryPasswordChangeRequiresMatchingPasswords() {
        assertThatThrownBy(() -> authController.changeForgottenPassword(
                new com.examly.springapp.dto.ResetPasswordDTO("NewPass@123", "Different@123"),
                otpAuthenticatedUser()))
                .isInstanceOf(IllegalStateException.class);

        verify(passwordRecoveryService, never()).changePassword(anyString(), anyString());
    }

    @Test
    void recoveryPasswordChangeDelegatesToTheRecoverySession() {
        authController.changeForgottenPassword(
                new com.examly.springapp.dto.ResetPasswordDTO("NewPass@123", "NewPass@123"),
                otpAuthenticatedUser());

        verify(passwordRecoveryService).changePassword("user@example.com", "NewPass@123");
    }

    private org.springframework.security.core.Authentication otpAuthenticatedUser() {
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated("user@example.com", null, java.util.List.of());
    }

    @Test
    void invalidPhoneOtpIsRejected() {
        when(twilioVerificationService.verifyOtp("9876543210", "000000")).thenReturn(false);

        assertThatThrownBy(() -> authController.verifyPhoneOtp(
                new com.examly.springapp.dto.VerifyPhoneOtpDTO("9876543210", "000000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approvedPhoneOtpIsReportedAsVerified() {
        when(twilioVerificationService.verifyOtp("9876543210", "123456")).thenReturn(true);

        ResponseEntity<Map<String, String>> response = authController.verifyPhoneOtp(
                new com.examly.springapp.dto.VerifyPhoneOtpDTO("9876543210", "123456"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsOnlyKeys("message");
        assertThat(response.getBody().get("message")).doesNotContain("123456");
    }

    @Test
    void phoneOtpEndpointsDoNotCreateAccounts() {
        authController.sendPhoneOtp(new com.examly.springapp.dto.PhoneOtpRequestDTO("9876543210"));

        verify(userService, never()).createUser(any());
        verify(userService, never()).createAdmin(any());
    }

    @Test
    void normalLoginStillReturnsAJwt() {
        User loggedIn = new User(9L, "user", "user@example.com", "encoded",
                "9876543210", "User");
        when(userService.loginUser(any(User.class))).thenReturn(loggedIn);
        when(jwtUtil.generateToken("user@example.com", "User", 9L)).thenReturn("jwt-token");

        ResponseEntity<LoginResponseDTO> response = authController.login(
                new com.examly.springapp.dto.LoginRequestDTO("user@example.com", "Secret@123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isEqualTo("jwt-token");
        assertThat(response.getBody().mustChangePassword()).isFalse();
    }
}
