package com.suwasarana.api.admin;

import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/verifications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVerificationController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/pending")
    public ResponseEntity<List<UserDto>> getPendingVerifications() {
        List<User> pendingUsers = userRepository.findByVerificationStatus(VerificationStatus.PENDING);
        List<UserDto> response = pendingUsers.stream()
                .map(u -> new UserDto(u.getId(), u.getEmail(), u.getPhoneNumber(), u.getRole().name(), u.getVerificationStatus().name()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}/approve")
    public ResponseEntity<Void> approveUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/reject")
    public ResponseEntity<Void> rejectUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setVerificationStatus(VerificationStatus.REJECTED);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    public static class UserDto {
        private Long id;
        private String email;
        private String phoneNumber;
        private String role;
        private String verificationStatus;

        public UserDto(Long id, String email, String phoneNumber, String role, String verificationStatus) {
            this.id = id;
            this.email = email;
            this.phoneNumber = phoneNumber;
            this.role = role;
            this.verificationStatus = verificationStatus;
        }

        public Long getId() { return id; }
        public String getEmail() { return email; }
        public String getPhoneNumber() { return phoneNumber; }
        public String getRole() { return role; }
        public String getVerificationStatus() { return verificationStatus; }
    }
}
