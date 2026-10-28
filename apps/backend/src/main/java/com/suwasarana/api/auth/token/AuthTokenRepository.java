package com.suwasarana.api.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHash(String tokenHash);

    Optional<AuthToken> findTopByUserIdAndTypeOrderByCreatedAtDesc(Long userId, AuthTokenType type);

    Optional<AuthToken> findTopByEmailAndTypeOrderByCreatedAtDesc(String email, AuthTokenType type);

    /**
     * Atomically spends a token: succeeds (returns 1) for exactly one caller, and only while the token is of the
     * expected type, unused and unexpired. Two concurrent requests with the same token cannot both succeed.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthToken t set t.usedAt = :now where t.tokenHash = :hash and t.type = :type and t.usedAt is null and t.expiresAt > :now")
    int markUsed(@Param("hash") String hash, @Param("type") AuthTokenType type, @Param("now") Instant now);

    /** Invalidates every still-open token of a type for a user (a new one replaces the old). */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthToken t set t.usedAt = :now where t.userId = :userId and t.type = :type and t.usedAt is null")
    int invalidateOpen(@Param("userId") Long userId, @Param("type") AuthTokenType type, @Param("now") Instant now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthToken t set t.usedAt = :now where t.email = :email and t.type = :type and t.usedAt is null")
    int invalidateOpenForEmail(@Param("email") String email, @Param("type") AuthTokenType type, @Param("now") Instant now);

    @Transactional
    @Modifying
    int deleteByExpiresAtBefore(Instant cutoff);

    @Transactional
    @Modifying
    int deleteByUserId(Long userId);

    boolean existsByEmailAndTypeAndUsedAtIsNullAndExpiresAtAfter(String email, AuthTokenType type, Instant now);
}
