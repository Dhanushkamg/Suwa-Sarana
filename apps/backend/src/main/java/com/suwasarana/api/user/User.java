package com.suwasarana.api.user;

import com.suwasarana.api.common.Times;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * An account. {@code @DynamicUpdate}: only changed columns are written, so a stale copy of the entity can never
 * undo an atomic counter update (failed_login_count, token_version) made through the repository.
 */
@Entity
@Table(name = "users")
@DynamicUpdate
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(length = 255)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    /** Legacy clear-text NIC column; never written any more (see nicHash). Emptied by NicHashBackfill. */
    @Column(unique = true, length = 20)
    private String nicNumber;

    /** HMAC-SHA256 of the normalised NIC; only used to detect duplicates. */
    @Column(length = 64, unique = true)
    private String nicHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    private Long verificationReviewedBy;
    private Instant verificationReviewedAt;
    @Column(length = 500)
    private String verificationReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;
    private Instant statusChangedAt;

    private Instant emailVerifiedAt;

    @Column(nullable = false)
    private int failedLoginCount = 0;
    private Instant lockedUntil;

    @Column(nullable = false)
    private int tokenVersion = 0;

    @Column(nullable = false)
    private boolean demoAccount = false;

    @Column(nullable = false, length = 2)
    private String preferredLanguage = "en";

    @Column(length = 30)
    private String consentVersion;
    private Instant consentedAt;
    private Instant deletedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = Times.utcNow();

    @Column(nullable = false)
    private LocalDateTime updatedAt = Times.utcNow();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Times.utcNow();
    }

    // ---- derived helpers --------------------------------------------------------------------------------

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean hasUsablePassword() {
        return passwordHash != null && !passwordHash.isBlank() && !passwordHash.startsWith("!");
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public AuthProvider getAuthProvider() { return authProvider; }
    public void setAuthProvider(AuthProvider authProvider) { this.authProvider = authProvider; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getNicNumber() { return nicNumber; }
    public void setNicNumber(String nicNumber) { this.nicNumber = nicNumber; }
    public String getNicHash() { return nicHash; }
    public void setNicHash(String nicHash) { this.nicHash = nicHash; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }
    public Long getVerificationReviewedBy() { return verificationReviewedBy; }
    public void setVerificationReviewedBy(Long verificationReviewedBy) { this.verificationReviewedBy = verificationReviewedBy; }
    public Instant getVerificationReviewedAt() { return verificationReviewedAt; }
    public void setVerificationReviewedAt(Instant verificationReviewedAt) { this.verificationReviewedAt = verificationReviewedAt; }
    public String getVerificationReason() { return verificationReason; }
    public void setVerificationReason(String verificationReason) { this.verificationReason = verificationReason; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
    public void setStatusChangedAt(Instant statusChangedAt) { this.statusChangedAt = statusChangedAt; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public void setEmailVerifiedAt(Instant emailVerifiedAt) { this.emailVerifiedAt = emailVerifiedAt; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public void setFailedLoginCount(int failedLoginCount) { this.failedLoginCount = failedLoginCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
    public int getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(int tokenVersion) { this.tokenVersion = tokenVersion; }
    public boolean isDemoAccount() { return demoAccount; }
    public void setDemoAccount(boolean demoAccount) { this.demoAccount = demoAccount; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public String getConsentVersion() { return consentVersion; }
    public void setConsentVersion(String consentVersion) { this.consentVersion = consentVersion; }
    public Instant getConsentedAt() { return consentedAt; }
    public void setConsentedAt(Instant consentedAt) { this.consentedAt = consentedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
