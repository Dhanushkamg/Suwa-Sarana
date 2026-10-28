package com.suwasarana.api.admin.dto;

import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RoleChangeDto {

    @NotNull(message = AuthCodes.ROLE_REQUIRED)
    private Role role;

    @NotBlank(message = AuthCodes.REASON_REQUIRED)
    @Size.List({
            @Size(min = 3, message = AuthCodes.REASON_REQUIRED),
            @Size(max = 500, message = AuthCodes.REASON_TOO_LONG)
    })
    private String reason;

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason == null ? null : reason.trim(); }
}
