package com.examly.springapp.service;

import com.examly.springapp.config.JwtUtils;
import com.examly.springapp.model.RevokedToken;
import com.examly.springapp.repository.RevokedTokenRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Server-side JWT revocation ("real" logout).
 *
 * <p>JWTs are stateless, so logging out used to only clear the browser's copy of the
 * token — a stolen token stayed valid for the full 24h lifetime. This service records
 * the SHA-256 hash of the token in {@code revoked_tokens} when the user logs out;
 * {@code JwtAuthenticationFilter} consults {@link #isRevoked(String)} on every request
 * and rejects revoked tokens with 401.</p>
 *
 * <p>Only hashes are stored — the raw token is never persisted — so the table is not
 * itself a credential. Rows are deleted once their token has expired (opportunistic
 * cleanup on each logout keeps the table bounded without a scheduler).</p>
 */
@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

    private final RevokedTokenRepo revokedTokenRepo;
    private final JwtUtils jwtUtil;
    private final long jwtExpirationMillis;

    public TokenRevocationService(RevokedTokenRepo revokedTokenRepo,
                                  JwtUtils jwtUtil,
                                  @Value("${jwt.expiration}") long jwtExpirationMillis) {
        this.revokedTokenRepo = revokedTokenRepo;
        this.jwtUtil = jwtUtil;
        this.jwtExpirationMillis = jwtExpirationMillis;
    }

    /**
     * Revokes the given raw JWT (called from POST /api/logout). The token must have
     * already passed the JWT filter, so its claims parse cleanly; a defensive fallback
     * keeps the row lifetime bounded even if that ever changes.
     */
    @Transactional
    public void revoke(String rawToken) {
        String hash = sha256Hex(rawToken);
        if (revokedTokenRepo.existsByTokenHash(hash)) {
            // Already revoked (double logout) — nothing to record.
            return;
        }

        RevokedToken revoked = new RevokedToken();
        revoked.setTokenHash(hash);
        revoked.setExpiresAt(resolveExpiry(rawToken));
        revoked.setCreatedAt(LocalDateTime.now());
        revokedTokenRepo.save(revoked);

        long purged = revokedTokenRepo.deleteByExpiresAtBefore(LocalDateTime.now());
        if (purged > 0) {
            log.debug("Purged {} expired revoked-token row(s)", purged);
        }
        log.info("Server-side logout recorded (token hash {}...)", hash.substring(0, 12));
    }

    /** True when this token was explicitly revoked by a logout. */
    public boolean isRevoked(String rawToken) {
        return revokedTokenRepo.existsByTokenHash(sha256Hex(rawToken));
    }

    /**
     * The token's own expiry, taken from its {@code exp} claim so the revocation row
     * lives exactly as long as the token could. Falls back to the configured lifetime
     * (converted to a wall-clock instant via the token's own iat when present).
     */
    private LocalDateTime resolveExpiry(String rawToken) {
        try {
            Date expiry = jwtUtil.extractExpiration(rawToken);
            if (expiry != null) {
                return LocalDateTime.ofInstant(expiry.toInstant(), ZoneId.systemDefault());
            }
        } catch (Exception ex) {
            log.warn("Could not read exp claim for a logout token; using configured lifetime");
        }
        Instant fallback = Instant.now().plusMillis(jwtExpirationMillis);
        return LocalDateTime.ofInstant(fallback, ZoneId.systemDefault());
    }

    /** SHA-256 hex digest — never store or log the raw token. */
    private String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                   .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JRE spec; this cannot happen on a normal JVM.
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
