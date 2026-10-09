package com.examly.springapp.service;

import com.examly.springapp.model.User;
import com.examly.springapp.repository.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PasswordRecoveryService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordRecoveryService.class);

    private record RecoveryCode(String code, Instant expiresAt, int attempts) {}

    /**
     * Reset authorization issued after a successful OTP verification. The opaque
     * token is returned to the client and bound server-side to the account email,
     * so completing the reset proves ownership without creating a login session.
     */
    private record ResetGrant(String token, Instant expiresAt) {}

    private final Map<String, RecoveryCode> recoveryCodes = new ConcurrentHashMap<>();
    private final Map<String, ResetGrant> resetGrants = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSentAt = new ConcurrentHashMap<>();
    private final UserRepo userRepo;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.otp.expiration-minutes:5}")
    private long expirationMinutes;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.otp.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    public PasswordRecoveryService(UserRepo userRepo, EmailService emailService,
                                   BCryptPasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    public void sendOtp(String email) {
        String normalizedEmail = normalize(email);
        User user = userRepo.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("No account exists for that email"));
        enforceResendCooldown(normalizedEmail);
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        recoveryCodes.put(normalizedEmail,
                new RecoveryCode(code, Instant.now().plusSeconds(expirationMinutes * 60), 0));
        lastSentAt.put(normalizedEmail, Instant.now());
        try {
            emailService.sendPasswordRecoveryOtpEmail(user.getEmail(), code);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // Mail transport details stay on the server, only the cause is logged.
            logger.error("Recovery OTP email could not be sent: {}", exception.getMessage());
            recoveryCodes.remove(normalizedEmail);
            lastSentAt.remove(normalizedEmail);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unable to send the recovery email right now. Please try again later");
        }
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
     * Verifies the recovery OTP (expiry, attempt limit, account association) and,
     * on success, issues a short-lived reset authorization for that account.
     * No login session or JWT is created here — the caller receives only the
     * opaque reset token needed to complete the password update.
     *
     * @return the reset authorization token bound to this account
     */
    public String verifyOtp(String email, String code) {
        String normalizedEmail = normalize(email);
        RecoveryCode stored = recoveryCodes.get(normalizedEmail);
        if (stored == null || !Instant.now().isBefore(stored.expiresAt())) {
            recoveryCodes.remove(normalizedEmail);
            throw new IllegalArgumentException("OTP is invalid or expired");
        }
        if (stored.attempts() >= maxAttempts) {
            recoveryCodes.remove(normalizedEmail);
            throw new IllegalArgumentException("Maximum OTP attempts exceeded");
        }
        if (!stored.code().equals(code)) {
            recoveryCodes.put(normalizedEmail,
                    new RecoveryCode(stored.code(), stored.expiresAt(), stored.attempts() + 1));
            throw new IllegalArgumentException("OTP is invalid or expired");
        }
        recoveryCodes.remove(normalizedEmail);
        if (userRepo.findByEmail(normalizedEmail).isEmpty()) {
            throw new IllegalArgumentException("Account no longer exists");
        }
        return issueResetGrant(normalizedEmail);
    }

    private String issueResetGrant(String normalizedEmail) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        // One active grant per account: re-verifying the OTP replaces the old grant.
        resetGrants.put(normalizedEmail,
                new ResetGrant(token, Instant.now().plusSeconds(expirationMinutes * 60)));
        return token;
    }

    /**
     * Completes the reset after OTP verification: validates the server-held,
     * time-limited grant for the account, hashes the new password with BCrypt,
     * then invalidates the grant and any outstanding OTP so it cannot be reused.
     */
    public void resetPasswordWithGrant(String email, String resetToken, String newPassword) {
        if (resetToken == null || resetToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password reset authorization is required");
        }
        String normalizedEmail = normalize(email);
        ResetGrant grant = resetGrants.get(normalizedEmail);
        if (grant == null || !Instant.now().isBefore(grant.expiresAt())) {
            resetGrants.remove(normalizedEmail);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password reset session is invalid or expired. Please verify the OTP again.");
        }
        if (!MessageDigest.isEqual(
                grant.token().getBytes(StandardCharsets.UTF_8),
                resetToken.trim().getBytes(StandardCharsets.UTF_8))) {
            // Wrong token for this account; the legitimate grant is left untouched.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password reset session is invalid or expired. Please verify the OTP again.");
        }
        User user = userRepo.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Account no longer exists"));
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepo.save(user);
        // Invalidate the reset authorization and any still-outstanding recovery OTP.
        resetGrants.remove(normalizedEmail);
        recoveryCodes.remove(normalizedEmail);
        lastSentAt.remove(normalizedEmail);
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
