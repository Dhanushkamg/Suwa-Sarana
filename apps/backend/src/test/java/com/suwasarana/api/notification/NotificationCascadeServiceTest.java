package com.suwasarana.api.notification;

import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.notification.dto.NotificationDto;
import com.suwasarana.api.notification.sms.SmsNotificationService;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.shared.AppClock;
import com.suwasarana.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCascadeServiceTest {

    @Mock
    private SseNotificationService sse;

    @Mock
    private SmsNotificationService sms;

    @Mock
    private NotificationLogRepository logRepository;

    @Mock
    private NotificationInboxService inbox;

    private NotificationCascadeService cascade(String language) {
        return new NotificationCascadeService(sse, sms, logRepository, inbox, UserLanguageLookup.fixed(language),
                AppClock.fixed(Instant.parse("2026-10-28T06:30:00Z")));
    }

    private DonorProfile donor;
    private BloodRequest request;
    private NotificationDto stored;

    @BeforeEach
    void setUp() {
        User user = new User();
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

        stored = new NotificationDto();
        stored.setId(77L);
        stored.setType("NEW_MATCH");
        stored.setBody("O+ blood needed at Colombo National Hospital (Colombo). Urgency: URGENT.");
        org.mockito.Mockito.lenient().when(inbox.persist(eq(10L), eq("NEW_MATCH"), any(), any(), any(), eq(500L))).thenReturn(stored);
    }

    @Test
    @DisplayName("The alert is stored in the inbox first, then pushed; a connected donor gets no SMS")
    void whenDonorConnected_storesInbox_pushesSse_andAvoidsSms() {
        when(sse.push(10L, stored)).thenReturn(2);

        cascade("en").sendNotification(donor, request);

        verify(inbox).persist(eq(10L), eq("NEW_MATCH"), eq("notifications.match.title"), eq("notifications.match.body"), any(Map.class), eq(500L));
        verify(sse).push(10L, stored);
        verify(sms, never()).sendSmsNotification(any(DonorProfile.class), any(NotificationMessage.class));

        ArgumentCaptor<NotificationLog> log = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(log.capture());
        assertEquals(NotificationChannel.SSE, log.getValue().getChannel());
        assertEquals(NotificationDeliveryStatus.SENT, log.getValue().getStatus());
    }

    @Test
    @DisplayName("No open stream: the donor is texted in English by default")
    void whenNotConnected_cascadesToEnglishSms() {
        when(sse.push(10L, stored)).thenReturn(0);
        when(sms.sendSmsNotification(any(DonorProfile.class), any(NotificationMessage.class))).thenReturn(true);

        cascade("en").sendNotification(donor, request);

        ArgumentCaptor<NotificationMessage> msg = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(sms).sendSmsNotification(eq(donor), msg.capture());
        assertEquals("Urgent blood request", msg.getValue().getTitle());
        assertTrue(msg.getValue().getBody().contains("O+ blood needed at Colombo National Hospital (Colombo)"));
        assertTrue(msg.getValue().getBody().contains("Urgency: URGENT"));

        ArgumentCaptor<NotificationLog> log = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(log.capture());
        assertEquals(NotificationChannel.SMS, log.getValue().getChannel());
        assertEquals(NotificationDeliveryStatus.SENT, log.getValue().getStatus());
        assertEquals("+94771234567", log.getValue().getRecipient());
    }

    @Test
    @DisplayName("The SMS follows the donor's preferred language (Sinhala)")
    void sms_isLocalisedToSinhala() {
        when(sse.push(10L, stored)).thenReturn(0);
        when(sms.sendSmsNotification(any(DonorProfile.class), any(NotificationMessage.class))).thenReturn(true);

        cascade("si").sendNotification(donor, request);

        ArgumentCaptor<NotificationMessage> msg = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(sms).sendSmsNotification(eq(donor), msg.capture());
        assertEquals("හදිසි රුධිර ඉල්ලීමක්", msg.getValue().getTitle());
        assertTrue(msg.getValue().getBody().contains("Colombo National Hospital (Colombo) හි O+ රුධිරය අවශ්‍යයි"));
        assertTrue(msg.getValue().getBody().contains("හදිසි"), "urgency word is localised");
    }

    @Test
    @DisplayName("The SMS follows the donor's preferred language (Tamil)")
    void sms_isLocalisedToTamil() {
        when(sse.push(10L, stored)).thenReturn(0);
        when(sms.sendSmsNotification(any(DonorProfile.class), any(NotificationMessage.class))).thenReturn(true);

        cascade("ta").sendNotification(donor, request);

        ArgumentCaptor<NotificationMessage> msg = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(sms).sendSmsNotification(eq(donor), msg.capture());
        assertEquals("அவசர இரத்தத் தேவை", msg.getValue().getTitle());
        assertTrue(msg.getValue().getBody().contains("இரத்தம் தேவை"));
    }

    @Test
    @DisplayName("An unknown language falls back to English")
    void unknownLanguage_fallsBackToEnglish() {
        assertEquals("en", NotificationTemplates.language("fr"));
        assertEquals("en", NotificationTemplates.language(null));
        assertEquals("si", NotificationTemplates.language("SI"));
        assertEquals("ta", NotificationTemplates.language("ta-LK"));
    }

    @Test
    @DisplayName("Records a FAILED audit log when the SMS fallback fails")
    void whenSmsFallbackFails_recordsFailedStatus() {
        when(sse.push(10L, stored)).thenReturn(0);
        when(sms.sendSmsNotification(any(DonorProfile.class), any(NotificationMessage.class))).thenReturn(false);

        cascade("en").sendNotification(donor, request);

        ArgumentCaptor<NotificationLog> log = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository, times(1)).save(log.capture());
        assertEquals(NotificationChannel.SMS, log.getValue().getChannel());
        assertEquals(NotificationDeliveryStatus.FAILED, log.getValue().getStatus());
    }

    @Test
    @DisplayName("Quiet hours keep the alert in the inbox but send nothing for non-CRITICAL requests")
    void whenInQuietHours_andNonCritical_onlyStoresInInbox() {
        donor.setQuietHoursStart(LocalTime.of(0, 0));
        donor.setQuietHoursEnd(LocalTime.of(23, 59));
        request.setUrgency(Urgency.ROUTINE);

        cascade("en").sendNotification(donor, request);

        verify(inbox).persist(eq(10L), eq("NEW_MATCH"), any(), any(), any(), eq(500L));
        verify(sse, never()).push(anyLong(), any());
        verify(sms, never()).sendSmsNotification(any(DonorProfile.class), any(NotificationMessage.class));

        ArgumentCaptor<NotificationLog> log = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(log.capture());
        assertEquals(NotificationDeliveryStatus.SKIPPED_QUIET_HOURS, log.getValue().getStatus());
    }

    @Test
    @DisplayName("CRITICAL requests bypass quiet hours")
    void whenInQuietHours_andCritical_bypassesQuietHours() {
        donor.setQuietHoursStart(LocalTime.of(0, 0));
        donor.setQuietHoursEnd(LocalTime.of(23, 59));
        request.setUrgency(Urgency.CRITICAL);
        when(sse.push(10L, stored)).thenReturn(1);

        cascade("en").sendNotification(donor, request);

        verify(sse).push(10L, stored);
    }

    @Test
    @DisplayName("Quiet hours use Sri Lanka time, not the server's zone")
    void quietHours_areEvaluatedInColomboTime() {
        // 2026-10-28T20:00Z is 01:30 on the 29th in Colombo
        NotificationCascadeService atNightInColombo = new NotificationCascadeService(sse, sms, logRepository, inbox,
                UserLanguageLookup.fixed("en"), AppClock.fixed(Instant.parse("2026-10-28T20:00:00Z")));
        donor.setQuietHoursStart(LocalTime.of(22, 0));
        donor.setQuietHoursEnd(LocalTime.of(7, 0));
        assertTrue(atNightInColombo.isInQuietHours(donor));

        NotificationCascadeService midday = cascade("en"); // 12:00 in Colombo
        assertFalse(midday.isInQuietHours(donor));
    }

    @Test
    @DisplayName("Quiet hours calculation evaluates midnight crossing correctly")
    void quietHoursEvaluation_handlesCrossMidnight() {
        NotificationCascadeService c = cascade("en");
        LocalTime start = LocalTime.of(22, 0);
        LocalTime end = LocalTime.of(7, 0);

        assertTrue(c.isTimeInQuietHours(LocalTime.of(23, 30), start, end));
        assertTrue(c.isTimeInQuietHours(LocalTime.of(3, 0), start, end));
        assertFalse(c.isTimeInQuietHours(LocalTime.of(12, 0), start, end));
        assertFalse(c.isTimeInQuietHours(LocalTime.of(18, 0), start, end));
    }
}
