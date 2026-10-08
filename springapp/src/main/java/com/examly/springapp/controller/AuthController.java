package com.examly.springapp.controller;

import com.examly.springapp.config.JwtUtils;
import com.examly.springapp.dto.*;
import com.examly.springapp.model.User;
import com.examly.springapp.service.EmailService;
import com.examly.springapp.service.OtpService;
import com.examly.springapp.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:8081")
public class AuthController {
    private final UserService userService;
    private final JwtUtils jwtUtil;
    private final OtpService otpService;
    private final EmailService emailService;

    public AuthController(UserService userService, JwtUtils jwtUtil,
                          OtpService otpService, EmailService emailService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.otpService = otpService;
        this.emailService = emailService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody UserRequestDTO request) {
        requireVerified(request.email());
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
        String otp = otpService.generateOtp(request.email());
        emailService.sendOtpEmail(request.email(), otp);
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

    private void requirePasswordChanged(Authentication authentication) {
        if (userService.mustChangePassword(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Change the initial password before using SuperAdmin management");
        }
    }
}