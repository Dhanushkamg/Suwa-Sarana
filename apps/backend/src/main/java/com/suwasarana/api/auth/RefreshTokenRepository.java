package com.suwasarana.api.auth;

import com.suwasarana.api.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Atomically claims a token for rotation: returns 1 for exactly one caller (the token was unused), 0 for everyone
     * else. This is what makes two concurrent refreshes with the same cookie yield ONE successor, not two.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.usedAt = :now where t.tokenHash = :hash and t.usedAt is null")
    int claim(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    int deleteByUser(User user);

    @Transactional
    @Modifying
    int deleteByExpiresAtBefore(Instant cutoff);

    long countByUser(User user);
}
