package com.suwasarana.api.notification;

import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.request.BloodRequest;

public interface NotificationService {
    void notify(DonorProfile donor, BloodRequest request);
}
