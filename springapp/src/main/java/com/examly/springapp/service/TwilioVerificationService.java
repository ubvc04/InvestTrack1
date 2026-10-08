package com.examly.springapp.service;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TwilioVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(TwilioVerificationService.class);

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.verify-service-sid:}")
    private String verifyServiceSid;

    @Value("${twilio.default-country-code:+91}")
    private String defaultCountryCode;

    @Value("${app.otp.phone-verification-ttl-minutes:10}")
    private long phoneVerificationTtlMinutes;

    /**
     * Temporary, in-memory proof that Twilio already approved a phone number.
     * Raw OTP codes are never stored: Twilio Verify owns their lifecycle.
     */
    private final Map<String, Instant> verifiedPhones = new ConcurrentHashMap<>();

    @PostConstruct
    void initialize() {
        if (!accountSid.isBlank() && !authToken.isBlank() && !verifyServiceSid.isBlank()) {
            Twilio.init(accountSid, authToken);
        }
    }

    public void sendOtp(String phoneNumber) {
        ensureConfigured();
        try {
            Verification.creator(verifyServiceSid, normalizePhone(phoneNumber), "sms").create();
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // Cause (Twilio error code) is logged for operators; credentials and
            // provider internals are never exposed to clients.
            logger.error("Twilio Verify could not send OTP to {}: code={} message={}",
                    normalizePhone(phoneNumber), twilioCode(exception), exception.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to send phone OTP right now. Please try again later");
        }
    }

    public boolean verifyOtp(String phoneNumber, String otp) {
        ensureConfigured();
        try {
            VerificationCheck check = VerificationCheck.creator(verifyServiceSid)
                    .setTo(normalizePhone(phoneNumber))
                    .setCode(otp)
                    .create();
            boolean approved = "approved".equalsIgnoreCase(check.getStatus());
            if (approved) {
                verifiedPhones.put(normalizePhone(phoneNumber),
                        Instant.now().plusSeconds(phoneVerificationTtlMinutes * 60));
            }
            return approved;
        } catch (ApiException exception) {
            // Wrong code, expired code or max attempts: Twilio rejects the check.
            logger.info("Twilio Verify rejected OTP for {}: code={} message={}",
                    normalizePhone(phoneNumber), twilioCode(exception), exception.getMessage());
            return false;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            logger.error("Twilio Verify OTP check failed for {}: message={}",
                    normalizePhone(phoneNumber), exception.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Phone verification is temporarily unavailable. Please try again later");
        }
    }

    /**
     * One-shot check: a verified phone number can be consumed exactly once,
     * which binds the verification to the account/registration that uses it.
     */
    public boolean consumeVerifiedPhone(String phoneNumber) {
        String normalized = normalizePhone(phoneNumber);
        Instant verifiedAt = verifiedPhones.get(normalized);
        if (verifiedAt == null) {
            return false;
        }
        if (Instant.now().isAfter(verifiedAt)) {
            verifiedPhones.remove(normalized);
            return false;
        }
        return verifiedPhones.remove(normalized, verifiedAt);
    }

    public String normalizePhone(String phoneNumber) {
        String compact = phoneNumber == null
                ? "" : phoneNumber.replaceAll("[\\s()-]", "");
        if (compact.matches("\\d{10}")) {
            return defaultCountryCode + compact;
        }
        return compact;
    }

    private void ensureConfigured() {
        if (accountSid.isBlank() || authToken.isBlank() || verifyServiceSid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Phone verification is not configured. Set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN "
                            + "and TWILIO_VERIFY_SERVICE_SID, then restart the server");
        }
    }

    private String twilioCode(RuntimeException exception) {
        if (exception instanceof ApiException apiException && apiException.getCode() != null) {
            return String.valueOf(apiException.getCode());
        }
        return "n/a";
    }
}
