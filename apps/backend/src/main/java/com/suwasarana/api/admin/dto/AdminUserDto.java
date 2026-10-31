package com.suwasarana.api.admin.dto;

import com.suwasarana.api.common.Times;

import com.suwasarana.api.user.User;

import java.time.Instant;
import java.time.LocalDateTime;

/** What an administrator sees about an account. Never contains the password hash, NIC data or tokens. */
public class AdminUserDto {
    private Long id;
    private String email;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private String role;
    private String verificationStatus;
    private String status;
    private boolean emailVerified;
    private String preferredLanguage;
    private String authProvider;
    private Instant registeredAt;
    private Long verificationReviewedBy;
    private Instant verificationReviewedAt;
    private String verificationReason;

    public static AdminUserDto of(User u) {
        AdminUserDto d = new AdminUserDto();
        d.id = u.getId();
        d.email = u.getEmail();
        d.phoneNumber = u.getPhoneNumber();
        d.firstName = u.getFirstName();
        d.lastName = u.getLastName();
        d.role = u.getRole().name();
        d.verificationStatus = u.getVerificationStatus().name();
        d.status = u.getStatus().name();
        d.emailVerified = u.isEmailVerified();
        d.preferredLanguage = u.getPreferredLanguage();
        d.authProvider = u.getAuthProvider().name();
        d.registeredAt = Times.instantOf(u.getCreatedAt());
        d.verificationReviewedBy = u.getVerificationReviewedBy();
        d.verificationReviewedAt = u.getVerificationReviewedAt();
        d.verificationReason = u.getVerificationReason();
        return d;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getRole() { return role; }
    public String getVerificationStatus() { return verificationStatus; }
    public String getStatus() { return status; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public String getAuthProvider() { return authProvider; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Long getVerificationReviewedBy() { return verificationReviewedBy; }
    public Instant getVerificationReviewedAt() { return verificationReviewedAt; }
    public String getVerificationReason() { return verificationReason; }
}
