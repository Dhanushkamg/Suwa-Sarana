package com.suwasarana.api.notification;

import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.notification.sms.SmsNotificationService;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationCascadeServiceTest {

    @Mock
    private SseNotificationService sseNotificationService;

    @Mock
    private SmsNotificationService smsNotificationService;

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @InjectMocks
    private NotificationCascadeService cascadeService;

    private DonorProfile donor;
    private BloodRequest request;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(10L);
        user.setEmail("donor10@example.com");
        user.setPhoneNumber("+94771234567");

        donor = new DonorProfile();
        donor.setId(100L);
        donor.setUser(user);
        donor.setBloodType("O+");
        donor.setDistrict("Colombo");

        request = new BloodRequest();
        request.setId(500L);
        request.setPatientBloodType("O+");
        request.setHospitalName("Colombo National Hospital");
        request.setDistrict("Colombo");
        request.setUrgency(Urgency.URGENT);
    }

    @Test
    @DisplayName("Should deliver via SSE when donor is connected and avoid SMS fallback")
    void whenDonorConnected_sendsSse_andAvoidsSmsFallback() {
        when(sseNotificationService.sendNotificationIfConnected(eq(10L), any(NotificationMessage.class)))
                .thenReturn(true);

        cascadeService.sendNotification(donor, request);

        verify(sseNotificationService, times(1)).sendNotificationIfConnected(eq(10L), any(NotificationMessage.class));
        verify(smsNotificationService, never()).sendSmsNotification(any(DonorProfile.class), any(BloodRequest.class));

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(1)).save(logCaptor.capture());

        NotificationLog savedLog = logCaptor.getValue();
        assertEquals(NotificationChannel.SSE, savedLog.getChannel());
        assertEquals(NotificationDeliveryStatus.SENT, savedLog.getStatus());
        assertEquals("donor10@example.com", savedLog.getRecipient());
    }

    @Test
    @DisplayName("Should fallback to SMS when donor is not connected via SSE")
    void whenDonorNotConnectedViaSse_cascadesToSms() {
        when(sseNotificationService.sendNotificationIfConnected(eq(10L), any(NotificationMessage.class)))
                .thenReturn(false);
        when(smsNotificationService.formatSmsMessage(request)).thenReturn("Mock formatted SMS body");
        when(smsNotificationService.sendSmsNotification(donor, request)).thenReturn(true);

        cascadeService.sendNotification(donor, request);

        verify(sseNotificationService, times(1)).sendNotificationIfConnected(eq(10L), any(NotificationMessage.class));
        verify(smsNotificationService, times(1)).sendSmsNotification(donor, request);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(1)).save(logCaptor.capture());

        NotificationLog savedLog = logCaptor.getValue();
        assertEquals(NotificationChannel.SMS, savedLog.getChannel());
        assertEquals(NotificationDeliveryStatus.SENT, savedLog.getStatus());
        assertEquals("+94771234567", savedLog.getRecipient());
    }

    @Test
    @DisplayName("Should record FAILED audit log when SMS fallback fails")
    void whenSmsFallbackFails_recordsFailedStatus() {
        when(sseNotificationService.sendNotificationIfConnected(eq(10L), any(NotificationMessage.class)))
                .thenReturn(false);
        when(smsNotificationService.formatSmsMessage(request)).thenReturn("Mock formatted SMS body");
        when(smsNotificationService.sendSmsNotification(donor, request)).thenReturn(false);

        cascadeService.sendNotification(donor, request);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(1)).save(logCaptor.capture());

        NotificationLog savedLog = logCaptor.getValue();
        assertEquals(NotificationChannel.SMS, savedLog.getChannel());
        assertEquals(NotificationDeliveryStatus.FAILED, savedLog.getStatus());
    }

    @Test
    @DisplayName("Should skip notification during quiet hours for non-CRITICAL requests")
    void whenInQuietHours_andNonCritical_skipsNotification() {
        // Configure quiet hours covering all day
        donor.setQuietHoursStart(LocalTime.of(0, 0));
        donor.setQuietHoursEnd(LocalTime.of(23, 59));
        request.setUrgency(Urgency.ROUTINE);

        cascadeService.sendNotification(donor, request);

        verify(sseNotificationService, never()).sendNotificationIfConnected(anyLong(), any());
        verify(smsNotificationService, never()).sendSmsNotification(any(DonorProfile.class), any(BloodRequest.class));

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(1)).save(logCaptor.capture());
        assertEquals(NotificationDeliveryStatus.SKIPPED_QUIET_HOURS, logCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("Should bypass quiet hours for CRITICAL blood requests")
    void whenInQuietHours_andCritical_bypassesQuietHours() {
        donor.setQuietHoursStart(LocalTime.of(0, 0));
        donor.setQuietHoursEnd(LocalTime.of(23, 59));
        request.setUrgency(Urgency.CRITICAL);

        when(sseNotificationService.sendNotificationIfConnected(eq(10L), any(NotificationMessage.class)))
                .thenReturn(true);

        cascadeService.sendNotification(donor, request);

        verify(sseNotificationService, times(1)).sendNotificationIfConnected(eq(10L), any(NotificationMessage.class));
        verify(notificationLogRepository, times(1)).save(any(NotificationLog.class));
    }

    @Test
    @DisplayName("Quiet hours calculation evaluates midnight crossing correctly")
    void quietHoursEvaluation_handlesCrossMidnight() {
        LocalTime start = LocalTime.of(22, 0); // 10 PM
        LocalTime end = LocalTime.of(7, 0);    // 7 AM

        assertTrue(cascadeService.isTimeInQuietHours(LocalTime.of(23, 30), start, end));
        assertTrue(cascadeService.isTimeInQuietHours(LocalTime.of(3, 0), start, end));
        assertFalse(cascadeService.isTimeInQuietHours(LocalTime.of(12, 0), start, end));
        assertFalse(cascadeService.isTimeInQuietHours(LocalTime.of(18, 0), start, end));
    }
}
