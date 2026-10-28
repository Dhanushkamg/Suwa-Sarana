package com.suwasarana.api.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Marks stock past its expiry date as EXPIRED, after midnight Colombo time and again every few hours as a safety net. */
@Component
public class InventoryExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(InventoryExpiryScheduler.class);

    @Autowired
    private InventoryService inventoryService;

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Colombo")
    public void expireDaily() {
        run();
    }

    @Scheduled(cron = "0 20 */6 * * *", zone = "Asia/Colombo")
    public void expireSafetyNet() {
        run();
    }

    private void run() {
        try {
            inventoryService.expireBatches();
        } catch (Exception e) {
            log.error("Stock expiry job failed", e);
        }
    }
}
