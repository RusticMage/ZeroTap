package com.zerotap.controller;

import com.zerotap.model.SafeSpace;
import com.zerotap.service.SafeSpaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/safe-spaces")
public class SafeSpaceController {

    private final SafeSpaceService safeSpaceService;

    public SafeSpaceController(SafeSpaceService safeSpaceService) {
        this.safeSpaceService = safeSpaceService;
    }

    @GetMapping
    public ResponseEntity<List<SafeSpace>> getAllSafeSpaces(@RequestParam(required = false) String type) {
        return ResponseEntity.ok(safeSpaceService.getAllSafeSpaces(type));
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<SafeSpaceService.SafeSpaceDistance>> getNearbySafeSpaces(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5000") double radiusMeters,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(safeSpaceService.getNearbySafeSpaces(latitude, longitude, radiusMeters, type));
    }
}
