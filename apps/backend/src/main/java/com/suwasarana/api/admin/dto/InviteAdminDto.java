package com.suwasarana.api.admin.dto;

import com.suwasarana.api.auth.AuthCodes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public class InviteAdminDto {

    @NotBlank(message = AuthCodes.EMAIL_REQUIRED)
    @Email(message = AuthCodes.EMAIL_INVALID)
    @Size(max = 254, message = AuthCodes.EMAIL_INVALID)
    private String email;

    @Size(max = 100, message = AuthCodes.NAME_TOO_LONG)
    private String firstName;

    /** Language of the invitation email (en | si | ta); defaults to en. */
    @Pattern(regexp = "^(en|si|ta)$", message = AuthCodes.LANGUAGE_INVALID)
    private String preferredLanguage;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT); }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName == null ? null : firstName.trim(); }
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
}
