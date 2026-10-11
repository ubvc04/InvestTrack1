package com.examly.springapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A JWT that was revoked by a server-side logout.
 *
 * <p>Only the SHA-256 hash of the token is stored — never the raw token — so a database
 * dump cannot be replayed as a credential. Rows become meaningless once
 * {@code expiresAt} passes (the token is expired anyway) and are deleted opportunistically
 * on every logout, which keeps the table bounded without a scheduler.</p>
 */
@Entity
@Table(name = "revoked_tokens", indexes = @Index(
        name = "idx_revoked_token_hash", columnList = "tokenHash"))
public class RevokedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 hex digest of the raw JWT (64 chars). The raw token is never persisted. */
    @Column(name = "tokenHash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    /** The token's own expiry time; the row is deleted once this passes. */
    @Column(name = "expiresAt", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    public RevokedToken() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
