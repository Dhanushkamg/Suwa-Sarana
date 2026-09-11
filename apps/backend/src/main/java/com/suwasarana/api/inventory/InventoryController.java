package com.suwasarana.api.inventory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @GetMapping("/stock-signals")
    public ResponseEntity<List<BloodInventoryDto>> getStockSignals() {
        return ResponseEntity.ok(inventoryService.getCriticalStockSignals());
    }
}
