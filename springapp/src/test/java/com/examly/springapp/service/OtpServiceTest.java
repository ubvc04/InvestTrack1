package com.examly.springapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Email OTP rules for the registration / admin-creation verification flow.
 */
class OtpServiceTest {

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService();
        ReflectionTestUtils.setField(otpService, "expirationMinutes", 5L);
        ReflectionTestUtils.setField(otpService, "maxAttempts", 5);
    }

    @Test
    void generatedOtpIsSixDigitsAndVerificationSucceedsOnce() {
        String otp = otpService.generateOtp("User@Example.com");

        assertThat(otp).matches("\\d{6}");
        assertThat(otpService.verifyOtp("user@example.com", otp)).isTrue();
        assertThat(otpService.isVerified("USER@example.com")).isTrue();

        otpService.removeOtp("user@example.com");
        assertThat(otpService.verifyOtp("user@example.com", otp)).isFalse();
    }

    @Test
    void wrongOtpIsRejectedAndMaxAttemptsEndsTheAttempt() {
        ReflectionTestUtils.setField(otpService, "maxAttempts", 2);
        String otp = otpService.generateOtp("user@example.com");

        assertThat(otpService.verifyOtp("user@example.com", "000000")).isFalse();
        assertThat(otpService.verifyOtp("user@example.com", "000000")).isFalse();
        // attempts exhausted: even the correct code is refused now
        assertThat(otpService.verifyOtp("user@example.com", otp)).isFalse();
        assertThat(otpService.isVerified("user@example.com")).isFalse();
    }

    @Test
    void expiredOtpIsRejected() {
        ReflectionTestUtils.setField(otpService, "expirationMinutes", 0L);
        String otp = otpService.generateOtp("user@example.com");

        assertThat(otpService.verifyOtp("user@example.com", otp)).isFalse();
        assertThat(otpService.isVerified("user@example.com")).isFalse();
    }

    @Test
    void otpIsBoundToItsOwnEmail() {
        String otp = otpService.generateOtp("first@example.com");

        assertThat(otpService.verifyOtp("second@example.com", otp)).isFalse();
        assertThat(otpService.isVerified("second@example.com")).isFalse();
        assertThat(otpService.verifyOtp("first@example.com", otp)).isTrue();
    }

    @Test
    void verifiedFlagIsClearedAfterRegistration() {
        String otp = otpService.generateOtp("user@example.com");
        otpService.verifyOtp("user@example.com", otp);
        otpService.removeVerified("user@example.com");

        assertThat(otpService.isVerified("user@example.com")).isFalse();
    }
}
