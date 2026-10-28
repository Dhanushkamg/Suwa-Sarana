package com.suwasarana.api.inventory;

import com.suwasarana.api.inventory.dto.InventoryDtos.StockSignalDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Public blood stock signals derived from the stock institutions record")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @Operation(summary = "Public stock shortages",
            description = "LOW and CRITICAL whole-blood/red-cell stock of verified institutions that changed or confirmed their stock in the last 48 hours. "
                    + "Plain array (not wrapped): [{hospitalName, district, bloodType, status, updatedAt}].")
    @GetMapping("/stock-signals")
    public ResponseEntity<List<StockSignalDto>> getStockSignals() {
        return ResponseEntity.ok(inventoryService.publicSignals());
    }
}
