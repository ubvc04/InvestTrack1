package com.examly.springapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phone verification behaviour that does not require live Twilio access.
 * Twilio Verify owns OTP generation, delivery and checking; this service only
 * normalises numbers, talks to the SDK and keeps the short-lived proof that a
 * number was approved.
 */
class TwilioVerificationServiceTest {

    private TwilioVerificationService twilioVerificationService;

    @BeforeEach
    void setUp() {
        twilioVerificationService = new TwilioVerificationService();
        ReflectionTestUtils.setField(twilioVerificationService, "accountSid", "");
        ReflectionTestUtils.setField(twilioVerificationService, "authToken", "");
        ReflectionTestUtils.setField(twilioVerificationService, "verifyServiceSid", "");
        ReflectionTestUtils.setField(twilioVerificationService, "defaultCountryCode", "+91");
        ReflectionTestUtils.setField(twilioVerificationService, "phoneVerificationTtlMinutes", 10L);
    }

    @Test
    void normalizesLocalNumbersToE164AndKeepsAlreadyInternationalNumbers() {
        assertThat(twilioVerificationService.normalizePhone("9876543210")).isEqualTo("+919876543210");
        assertThat(twilioVerificationService.normalizePhone("+91 98765 43210")).isEqualTo("+919876543210");
        assertThat(twilioVerificationService.normalizePhone("+1 (555) 123-4567")).isEqualTo("+15551234567");
        assertThat(twilioVerificationService.normalizePhone(null)).isEmpty();
    }

    @Test
    void unconfiguredTwilioFailsCleanlyInsteadOfLeakingInternals() {
        assertThatThrownBy(() -> twilioVerificationService.sendOtp("9876543210"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .hasMessageContaining("not configured");

        assertThatThrownBy(() -> twilioVerificationService.verifyOtp("9876543210", "123456"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void phoneIsNotConsideredVerifiedUntilTwilioApprovesIt() {
        // No Twilio approval has happened, so no verification proof exists.
        assertThat(twilioVerificationService.consumeVerifiedPhone("9876543210")).isFalse();
        assertThat(twilioVerificationService.consumeVerifiedPhone("+919876543210")).isFalse();
    }

    @Test
    void verificationIsBoundToTheExactPhoneNumber() {
        ReflectionTestUtils.setField(twilioVerificationService, "accountSid", "AC_test");
        ReflectionTestUtils.setField(twilioVerificationService, "authToken", "test-token");
        ReflectionTestUtils.setField(twilioVerificationService, "verifyServiceSid", "VA_test");

        // simulate the state Twilio leaves behind after an approved verification check
        ReflectionTestUtils.setField(twilioVerificationService, "verifiedPhones",
                new java.util.concurrent.ConcurrentHashMap<>(java.util.Map.of("+919876543210",
                        java.time.Instant.now().plusSeconds(600))));

        assertThat(twilioVerificationService.consumeVerifiedPhone("+919876543210")).isTrue();
        // one shot: a second registration cannot reuse the same approval
        assertThat(twilioVerificationService.consumeVerifiedPhone("+919876543210")).isFalse();
        // and it never applies to a different number
        assertThat(twilioVerificationService.consumeVerifiedPhone("+919123456789")).isFalse();
    }

    @Test
    void expiredVerificationProofIsRejected() {
        ReflectionTestUtils.setField(twilioVerificationService, "verifiedPhones",
                new java.util.concurrent.ConcurrentHashMap<>(java.util.Map.of("+919876543210",
                        java.time.Instant.now().minusSeconds(1))));

        assertThat(twilioVerificationService.consumeVerifiedPhone("9876543210")).isFalse();
    }
}
