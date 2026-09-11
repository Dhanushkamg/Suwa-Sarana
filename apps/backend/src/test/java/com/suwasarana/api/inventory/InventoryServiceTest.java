package com.suwasarana.api.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InventoryServiceTest {

    @Mock
    private BloodInventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    public void testGetCriticalStockSignals() {
        BloodInventory inv1 = new BloodInventory();
        inv1.setId(1L);
        inv1.setHospitalName("Jaffna Teaching Hospital");
        inv1.setDistrict("Jaffna");
        inv1.setBloodType("O-");
        inv1.setStatus("CRITICAL");

        BloodInventory inv2 = new BloodInventory();
        inv2.setId(2L);
        inv2.setHospitalName("Kandy General");
        inv2.setDistrict("Kandy");
        inv2.setBloodType("B+");
        inv2.setStatus("LOW");

        when(inventoryRepository.findByStatusIn(List.of("LOW", "CRITICAL"))).thenReturn(List.of(inv1, inv2));

        List<BloodInventoryDto> signals = inventoryService.getCriticalStockSignals();
        assertEquals(2, signals.size());
        assertEquals("Jaffna Teaching Hospital", signals.get(0).getHospitalName());
        assertEquals("CRITICAL", signals.get(0).getStatus());
        assertEquals("LOW", signals.get(1).getStatus());
    }
}
