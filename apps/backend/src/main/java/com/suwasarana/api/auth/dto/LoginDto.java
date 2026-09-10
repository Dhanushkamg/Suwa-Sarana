package com.suwasarana.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public class LoginDto {

    @NotBlank(message = "Username (email or phone) is required")
    @JsonAlias({"email", "phoneNumber", "phone"})
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    // Getters and Setters
    public String getUsername() { 
        return username; 
    }
    
    public void setUsername(String username) { 
        this.username = username; 
    }

    public String getEmail() {
        return username;
    }

    public void setEmail(String email) {
        if (this.username == null || this.username.isBlank()) {
            this.username = email;
        }
    }

    public String getPassword() { 
        return password; 
    }
    
    public void setPassword(String password) { 
        this.password = password; 
    }
}
