package com.suwasarana.api.user.dto;

import com.suwasarana.api.user.User;

import java.time.Instant;
import java.time.LocalDateTime;

/** The signed-in user's own profile. */
public class MeDto {
    private Long id;
    private String email;
    private boolean emailVerified;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private String role;
    private String verificationStatus;
    private String verificationReason;
    private String status;
    private String preferredLanguage;
    private String authProvider;
    private boolean hasPassword;
    private boolean googleLinked;
    private String consentVersion;
    private Instant consentedAt;
    /** True when the user has not yet accepted the current terms version (ask them to accept via POST /api/users/me/consent). */
    private boolean consentRequired;
    private LocalDateTime createdAt;

    public static MeDto of(User u, String currentConsentVersion) {
        MeDto d = new MeDto();
        d.id = u.getId();
        d.email = u.getEmail();
        d.emailVerified = u.isEmailVerified();
        d.phoneNumber = u.getPhoneNumber();
        d.firstName = u.getFirstName();
        d.lastName = u.getLastName();
        d.role = u.getRole().name();
        d.verificationStatus = u.getVerificationStatus().name();
        d.verificationReason = u.getVerificationReason();
        d.status = u.getStatus().name();
        d.preferredLanguage = u.getPreferredLanguage();
        d.authProvider = u.getAuthProvider().name();
        d.hasPassword = u.hasUsablePassword();
        d.googleLinked = u.getProviderId() != null;
        d.consentVersion = u.getConsentVersion();
        d.consentedAt = u.getConsentedAt();
        d.consentRequired = u.getConsentVersion() == null || !u.getConsentVersion().equals(currentConsentVersion);
        d.createdAt = u.getCreatedAt();
        return d;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getRole() { return role; }
    public String getVerificationStatus() { return verificationStatus; }
    public String getVerificationReason() { return verificationReason; }
    public String getStatus() { return status; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public String getAuthProvider() { return authProvider; }
    public boolean isHasPassword() { return hasPassword; }
    public boolean isGoogleLinked() { return googleLinked; }
    public String getConsentVersion() { return consentVersion; }
    public Instant getConsentedAt() { return consentedAt; }
    public boolean isConsentRequired() { return consentRequired; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
