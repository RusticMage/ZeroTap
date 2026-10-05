package com.zerotap.controller;

import com.zerotap.dto.LocationUpdateDto;
import com.zerotap.model.LocationRecord;
import com.zerotap.service.LocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PostMapping
    public ResponseEntity<LocationRecord> recordLocation(
            @RequestBody LocationUpdateDto dto,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String userId,
            Authentication authentication) {
        String effectiveUser = authentication != null ? authentication.getName() : 
                               (userId != null && !userId.isBlank()) ? userId :
                               (deviceId != null && !deviceId.isBlank()) ? deviceId : "user-device-1";
        return ResponseEntity.ok(locationService.recordLocation(effectiveUser, deviceId, dto));
    }

    @GetMapping("/active")
    public ResponseEntity<List<LocationRecord>> getActiveDevices() {
        return ResponseEntity.ok(locationService.getActiveDevicesLocations());
    }

    @GetMapping("/users/{userId}/latest")
    public ResponseEntity<LocationRecord> getLatestUserLocation(@PathVariable String userId) {
        return locationService.getLatestLocation(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/users/{userId}/history")
    public ResponseEntity<List<LocationRecord>> getUserLocationHistory(@PathVariable String userId) {
        return ResponseEntity.ok(locationService.getLocationHistory(userId));
    }
}
