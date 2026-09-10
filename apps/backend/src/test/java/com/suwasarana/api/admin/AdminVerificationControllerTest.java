package com.suwasarana.api.admin;

import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminVerificationControllerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminVerificationController adminVerificationController;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(1L);
        sampleUser.setEmail("pending@test.com");
        sampleUser.setPhoneNumber("0711111111");
        sampleUser.setRole(Role.HOSPITAL_REQUESTER);
        sampleUser.setVerificationStatus(VerificationStatus.PENDING);
    }

    @Test
    void getPendingVerifications_shouldReturnPendingUsers() {
        when(userRepository.findByVerificationStatus(VerificationStatus.PENDING)).thenReturn(List.of(sampleUser));

        ResponseEntity<List<AdminVerificationController.UserDto>> response = adminVerificationController.getPendingVerifications();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals("pending@test.com", response.getBody().get(0).getEmail());
    }

    @Test
    void approveUser_shouldSetStatusToVerified() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        ResponseEntity<Void> response = adminVerificationController.approveUser(1L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(VerificationStatus.VERIFIED, sampleUser.getVerificationStatus());
        verify(userRepository).save(sampleUser);
    }

    @Test
    void rejectUser_shouldSetStatusToRejected() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        ResponseEntity<Void> response = adminVerificationController.rejectUser(1L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(VerificationStatus.REJECTED, sampleUser.getVerificationStatus());
        verify(userRepository).save(sampleUser);
    }
}
