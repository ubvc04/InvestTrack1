package com.examly.springapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private record OtpCode(String code, Instant expiresAt, int attempts) {}

    private final Map<String, OtpCode> otpStorage = new ConcurrentHashMap<>();

    /** Last send time per email — drives the resend cooldown / rate limit. */
    private final Map<String, Instant> lastSentAt = new ConcurrentHashMap<>();

    private final Set<String> verifiedEmails = ConcurrentHashMap.newKeySet();

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.otp.expiration-minutes:5}")
    private long expirationMinutes;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.otp.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    public String generateOtp(String email) {
        String normalizedEmail = normalize(email);

        enforceResendCooldown(normalizedEmail);

        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));

        otpStorage.put(normalizedEmail,
                new OtpCode(otp, Instant.now().plusSeconds(expirationMinutes * 60), 0));
        lastSentAt.put(normalizedEmail, Instant.now());

        return otp;
    }

    private void enforceResendCooldown(String normalizedEmail) {
        Instant previous = lastSentAt.get(normalizedEmail);
        if (previous == null) {
            return;
        }
        long elapsedSeconds = Duration.between(previous, Instant.now()).getSeconds();
        if (elapsedSeconds < resendCooldownSeconds) {
            long waitSeconds = resendCooldownSeconds - elapsedSeconds;
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait " + waitSeconds + " second(s) before requesting another OTP");
        }
    }

    /**
     * Clears the resend cooldown (used when the OTP email could not be delivered,
     * so the user can retry immediately instead of waiting out the timer).
     */
    public void removeSendCooldown(String email) {
        lastSentAt.remove(normalize(email));
    }

    public boolean verifyOtp(
            String email,
            String otp) {

        String normalizedEmail = normalize(email);
        OtpCode stored = otpStorage.get(normalizedEmail);

        if (stored == null || !Instant.now().isBefore(stored.expiresAt())) {
            otpStorage.remove(normalizedEmail);
            return false;
        }
        if (stored.attempts() >= maxAttempts) {
            otpStorage.remove(normalizedEmail);
            return false;
        }
        if (otp == null || !otp.equals(stored.code())) {
            otpStorage.put(normalizedEmail,
                    new OtpCode(stored.code(), stored.expiresAt(), stored.attempts() + 1));
            return false;
        }

        verifiedEmails.add(normalizedEmail);
        return true;
    }

    public boolean isVerified(String email) {
        return verifiedEmails.contains(normalize(email));
    }

    public void removeVerified(String email) {
        verifiedEmails.remove(normalize(email));
    }

    public void removeOtp(String email) {
        otpStorage.remove(normalize(email));
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
