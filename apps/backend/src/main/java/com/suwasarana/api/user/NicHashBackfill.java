package com.suwasarana.api.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-off, idempotent: moves every legacy clear-text NIC (users.nic_number) to the keyed hash (users.nic_hash) and
 * clears the clear-text column. Runs at startup; does nothing once the column is empty. A NIC that is invalid or
 * would collide with another account's hash is simply dropped (it was never used for anything else).
 */
@Component
public class NicHashBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NicHashBackfill.class);

    private final UserRepository userRepository;
    private final NicHasher nicHasher;

    public NicHashBackfill(UserRepository userRepository, NicHasher nicHasher) {
        this.userRepository = userRepository;
        this.nicHasher = nicHasher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> legacy;
        try {
            legacy = userRepository.findWithLegacyNic();
        } catch (RuntimeException e) {
            log.warn("NIC backfill skipped: {}", e.getClass().getSimpleName());
            return;
        }
        if (legacy.isEmpty()) {
            return;
        }
        int hashed = 0;
        int dropped = 0;
        for (User user : legacy) {
            String hash = nicHasher.hash(user.getNicNumber());
            if (hash != null && user.getNicHash() == null && !userRepository.existsByNicHash(hash)) {
                user.setNicHash(hash);
                hashed++;
            } else {
                dropped++;
            }
            user.setNicNumber(null);
            userRepository.saveAndFlush(user);
        }
        log.info("NIC backfill: {} hashed, {} dropped, clear-text NIC column emptied", hashed, dropped);
    }
}
