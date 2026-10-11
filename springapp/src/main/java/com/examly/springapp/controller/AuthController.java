package com.examly.springapp.controller;

import com.examly.springapp.config.JwtUtils;
import com.examly.springapp.dto.*;
import com.examly.springapp.model.User;
import com.examly.springapp.service.EmailService;
import com.examly.springapp.service.OtpService;
import com.examly.springapp.service.PasswordRecoveryService;
import com.examly.springapp.service.TokenRevocationService;
import com.examly.springapp.service.TwilioVerificationService;
import com.examly.springapp.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:8081")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    /**
     * Password policy for self-service registration — mirrors the live rules shown
     * on the registration page (min 8 chars, upper, lower, digit, special char).
     */
    private static final Pattern STRONG_PASSWORD =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");
    private static final String STRONG_PASSWORD_MESSAGE =
            "Password must be at least 8 characters and include an uppercase letter, "
            + "a lowercase letter, a number and a special character";

    private final UserService userService;
    private final JwtUtils jwtUtil;
    private final OtpService otpService;
    private final EmailService emailService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final TwilioVerificationService twilioVerificationService;
    private final TokenRevocationService tokenRevocationService;

    public AuthController(UserService userService, JwtUtils jwtUtil,
                          OtpService otpService, EmailService emailService,
                          PasswordRecoveryService passwordRecoveryService,
                          TwilioVerificationService twilioVerificationService,
                          TokenRevocationService tokenRevocationService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.otpService = otpService;
        this.emailService = emailService;
        this.passwordRecoveryService = passwordRecoveryService;
        this.twilioVerificationService = twilioVerificationService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody UserRequestDTO request) {
        if (request.password() == null || !STRONG_PASSWORD.matcher(request.password()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, STRONG_PASSWORD_MESSAGE);
        }
        requireVerified(request.email());
        requirePhoneVerified(request.mobileNumber());
        User created = userService.createUser(ApiDtoMapper.toUser(request));
        otpService.removeVerified(request.email());
        otpService.removeOtp(request.email());
        return ResponseEntity.status(201).body(ApiDtoMapper.toUserResponse(created));
    }

    @PostMapping("/admins")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SuperAdmin')")
    public ResponseEntity<UserResponseDTO> createAdmin(
            @Valid @RequestBody UserRequestDTO request, Authentication authentication) {
        requirePasswordChanged(authentication);
        requireVerified(request.email());
        requirePhoneVerified(request.mobileNumber());
        User created = userService.createAdmin(ApiDtoMapper.toUser(request));
        otpService.removeVerified(request.email());
        otpService.removeOtp(request.email());
        return ResponseEntity.status(201).body(ApiDtoMapper.toUserResponse(created));
    }

    @GetMapping("/admins")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SuperAdmin')")
    public List<UserResponseDTO> getAdmins(Authentication authentication) {
        requirePasswordChanged(authentication);
        return userService.getAdmins().stream().map(ApiDtoMapper::toUserResponse).toList();
    }

    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, String>> sendOtp(@Valid @RequestBody SendOtpRequestDTO request) {
        // Server-side email uniqueness gate: no OTP is generated or sent for an
        // address that already has an account (also enforced at register time).
        if (userService.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This email is already registered. Please sign in or use another email address.");
        }
        String otp = otpService.generateOtp(request.email());
        try {
            emailService.sendOtpEmail(request.email(), otp);
        } catch (RuntimeException exception) {
            // Cause is logged for operators; the SMTP detail never reaches the client.
            logger.error("Registration OTP email could not be sent: {}", exception.getMessage());
            otpService.removeOtp(request.email());
            otpService.removeSendCooldown(request.email());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unable to send OTP right now. Please try again later");
        }
        return ResponseEntity.ok(Map.of("message", "OTP sent successfully"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(@Valid @RequestBody VerifyOtpRequestDTO request) {
        if (!otpService.verifyOtp(request.email(), request.otp())) {
            throw new IllegalStateException("Invalid OTP");
        }
        otpService.removeOtp(request.email());
        return ResponseEntity.ok(Map.of("message", "OTP verified successfully"));
    }

    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<Map<String, String>> sendForgotPasswordOtp(
                @Valid @RequestBody ForgotPasswordRequestDTO request) {
        passwordRecoveryService.sendOtp(request.email());
        return ResponseEntity.ok(Map.of("message", "Password recovery OTP sent successfully"));
    }

    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<Map<String, String>> verifyForgotPasswordOtp(
                @Valid @RequestBody VerifyForgotPasswordOtpDTO request) {
        // Verification never logs the user in. It only issues a short-lived,
        // server-validated reset authorization bound to this account.
        String resetToken = passwordRecoveryService.verifyOtp(request.email(), request.otp());
        return ResponseEntity.ok(Map.of(
                "message", "OTP verified successfully",
                "resetToken", resetToken));
    }

    @PutMapping("/forgot-password/change-password")
    public ResponseEntity<Map<String, String>> changeForgottenPassword(
                @Valid @RequestBody ResetPasswordDTO request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalStateException("Passwords do not match");
        }
        passwordRecoveryService.resetPasswordWithGrant(
                request.email(), request.resetToken(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    @PostMapping("/phone/send-otp")
    public ResponseEntity<Map<String, String>> sendPhoneOtp(
                @Valid @RequestBody PhoneOtpRequestDTO request) {
        twilioVerificationService.sendOtp(request.phoneNumber());
        return ResponseEntity.ok(Map.of("message", "Phone OTP sent successfully"));
    }

    @PostMapping("/phone/verify-otp")
    public ResponseEntity<Map<String, String>> verifyPhoneOtp(
                @Valid @RequestBody VerifyPhoneOtpDTO request) {
        if (!twilioVerificationService.verifyOtp(request.phoneNumber(), request.otp())) {
            throw new IllegalArgumentException("Invalid or expired phone OTP");
        }
        return ResponseEntity.ok(Map.of("message", "Phone number verified successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        User login = new User();
        login.setEmail(request.email());
        login.setPassword(request.password());
        User loggedIn = userService.loginUser(login);
        String token = jwtUtil.generateToken(loggedIn.getEmail(), loggedIn.getUserRole(), loggedIn.getUserId());
        return ResponseEntity.ok(new LoginResponseDTO(token, loggedIn.getUsername(),
                loggedIn.getUserRole(), loggedIn.getUserId(), loggedIn.isMustChangePassword()));
    }

    /**
     * Server-side logout: revokes the presented JWT so it can no longer authenticate
     * any request, even if a copy of it survives outside the browser. The revocation
     * is recorded as a SHA-256 hash in {@code revoked_tokens}; JwtAuthenticationFilter
     * rejects revoked tokens with 401. Only tokens that passed the filter reach here,
     * so the header is always present and valid.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenRevocationService.revoke(authHeader.substring(7));
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PutMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequestDTO request, Authentication authentication) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalStateException("Passwords do not match");
        }
        userService.changePassword(authentication.getName(), request.oldPassword(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    private void requireVerified(String email) {
        if (!otpService.isVerified(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Please verify your email before registration");
        }
    }

    private void requirePhoneVerified(String phoneNumber) {
        if (!twilioVerificationService.consumeVerifiedPhone(phoneNumber)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Please verify your phone number before continuing");
        }
    }

    private void requirePasswordChanged(Authentication authentication) {
        if (userService.mustChangePassword(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Change the initial password before using SuperAdmin management");
        }
    }
}