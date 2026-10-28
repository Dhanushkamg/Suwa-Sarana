package com.suwasarana.api.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByPhoneNumber(String phoneNumber);
    Optional<User> findByProviderId(String providerId);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByNicHash(String nicHash);
    java.util.List<User> findByVerificationStatus(VerificationStatus status);

    Optional<User> findFirstByPhoneNumberIn(Collection<String> phoneNumbers);

    boolean existsByPhoneNumberInAndIdNot(Collection<String> phoneNumbers, Long id);

    @Query("select u from User u where u.nicNumber is not null")
    java.util.List<User> findWithLegacyNic();

    /** Accounts waiting for a decision, limited to the given roles. */
    Page<User> findByVerificationStatusAndRoleInAndStatus(VerificationStatus verificationStatus, Collection<Role> roles,
                                                          UserStatus status, Pageable pageable);

    /** Usable administrators: not demo/seeded, active, able to sign in with a real password. */
    @Query("""
            select count(u) from User u
            where u.role = com.suwasarana.api.user.Role.ADMIN
              and u.status = com.suwasarana.api.user.UserStatus.ACTIVE
              and u.demoAccount = false
              and u.passwordHash is not null and u.passwordHash not like '!%'
            """)
    long countUsableAdmins();

    // ---- atomic counters (no read-modify-write races) ----------------------------------------------------

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.failedLoginCount = u.failedLoginCount + 1 where u.id = :id")
    int incrementFailedLogins(@Param("id") Long id);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.lockedUntil = :until where u.id = :id")
    int setLockedUntil(@Param("id") Long id, @Param("until") Instant until);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.failedLoginCount = 0, u.lockedUntil = null where u.id = :id")
    int clearLoginFailures(@Param("id") Long id);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.tokenVersion = u.tokenVersion + 1 where u.id = :id")
    int bumpTokenVersion(@Param("id") Long id);
}
