package com.suwasarana.api.geo;

import com.suwasarana.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Read-only reference data shared by the frontend and the backend. */
@RestController
@RequestMapping("/api/meta")
@Tag(name = "Meta", description = "Read-only reference data (districts, geographic bounds)")
public class MetaController {

    @Operation(summary = "The 25 official districts", description = "Canonical names and centre points; the same list the API validates against.")
    @GetMapping("/districts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> districts() {
        List<Map<String, Object>> items = District.all().stream()
                .map(d -> Map.<String, Object>of("name", d.name(), "latitude", d.latitude(), "longitude", d.longitude()))
                .toList();
        Map<String, Object> bounds = Map.of(
                "minLat", GeoBounds.MIN_LAT, "maxLat", GeoBounds.MAX_LAT,
                "minLon", GeoBounds.MIN_LON, "maxLon", GeoBounds.MAX_LON);
        return ResponseEntity.ok(ApiResponse.success(Map.of("districts", items, "bounds", bounds)));
    }
}
