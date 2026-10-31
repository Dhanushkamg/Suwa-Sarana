package com.suwasarana.api.notification;

import com.suwasarana.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Reads a user's {@code users.preferred_language} (en / si / ta; set at registration or in the profile) so alerts,
 * SMS and inbox texts are rendered in the recipient's language. Any miss (unknown user, null, unsupported value)
 * falls back to English.
 */
@Component
public class UserLanguageLookup {

    private final UserRepository userRepository;

    @Autowired
    public UserLanguageLookup(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Test seam: a lookup that always answers {@code lang}. */
    public static UserLanguageLookup fixed(String lang) {
        return new UserLanguageLookup(null) {
            @Override
            public String languageOf(Long userId) {
                return lang;
            }
        };
    }

    public String languageOf(Long userId) {
        if (userId == null || userRepository == null) return NotificationTemplates.EN;
        return userRepository.findPreferredLanguageById(userId)
                .map(NotificationTemplates::language)
                .orElse(NotificationTemplates.EN);
    }
}
