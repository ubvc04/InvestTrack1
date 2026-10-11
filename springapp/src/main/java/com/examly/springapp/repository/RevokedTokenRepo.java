package com.examly.springapp.repository;

import com.examly.springapp.model.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface RevokedTokenRepo extends JpaRepository<RevokedToken, Long> {

    /** O(1) logout check against the indexed token hash. */
    boolean existsByTokenHash(String tokenHash);

    /**
     * Opportunistic cleanup: once a token has expired it can never be replayed, so its
     * revocation row is dead weight. Called on every logout, which keeps the table
     * bounded without needing a background scheduler.
     */
    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
