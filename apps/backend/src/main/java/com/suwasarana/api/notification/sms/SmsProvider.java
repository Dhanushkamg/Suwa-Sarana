package com.suwasarana.api.notification.sms;

public interface SmsProvider {
    /**
     * Dispatches an SMS message to the specified recipient phone number.
     * @param recipientPhone The destination phone number.
     * @param message The SMS text message body.
     * @return true if successfully dispatched, false otherwise.
     */
    boolean sendSms(String recipientPhone, String message);
}
