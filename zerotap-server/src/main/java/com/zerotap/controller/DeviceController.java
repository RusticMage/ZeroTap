package com.zerotap.controller;

import com.zerotap.dto.DeviceRegistrationRequest;
import com.zerotap.model.Device;
import com.zerotap.service.DeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/register")
    public ResponseEntity<Device> registerDevice(@RequestBody DeviceRegistrationRequest request, Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : "anonymous_user";
        return ResponseEntity.ok(deviceService.registerDevice(userId, request));
    }
}
