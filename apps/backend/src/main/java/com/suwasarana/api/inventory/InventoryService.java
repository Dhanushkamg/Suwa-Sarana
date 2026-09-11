package com.suwasarana.api.inventory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    @Autowired
    private BloodInventoryRepository inventoryRepository;

    public List<BloodInventoryDto> getCriticalStockSignals() {
        // Return only LOW and CRITICAL stock levels to public
        return inventoryRepository.findByStatusIn(List.of("LOW", "CRITICAL"))
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private BloodInventoryDto mapToDto(BloodInventory inv) {
        BloodInventoryDto dto = new BloodInventoryDto();
        dto.setHospitalName(inv.getHospitalName());
        dto.setDistrict(inv.getDistrict());
        dto.setBloodType(inv.getBloodType());
        dto.setStatus(inv.getStatus());
        dto.setUpdatedAt(inv.getUpdatedAt());
        return dto;
    }
}
