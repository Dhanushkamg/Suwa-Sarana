package com.suwasarana.api.notification;

import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.request.BloodRequest;

public interface NotificationService {
    /** Sends an emergency blood request notification through the SSE → SMS cascade. */
    void notify(DonorProfile donor, BloodRequest request);

    /** Sends a generic pre-built notification (e.g. slot confirmation, reminders) through the same cascade. */
    void notify(DonorProfile donor, NotificationMessage message);
}

