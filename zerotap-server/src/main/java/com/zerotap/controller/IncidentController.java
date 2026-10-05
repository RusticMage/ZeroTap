package com.zerotap.controller;

import com.zerotap.dto.CheckInResponseDto;
import com.zerotap.dto.IncidentDto;
import com.zerotap.dto.PingRequestDto;
import com.zerotap.model.CheckIn;
import com.zerotap.model.Incident;
import com.zerotap.service.CheckInService;
import com.zerotap.service.IncidentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;
    private final CheckInService checkInService;

    public IncidentController(IncidentService incidentService, CheckInService checkInService) {
        this.incidentService = incidentService;
        this.checkInService = checkInService;
    }

    @PostMapping
    public ResponseEntity<Incident> createOrSyncIncident(@RequestBody IncidentDto dto, Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : (dto.getUserId() != null ? dto.getUserId() : "user-device-1");
        return ResponseEntity.ok(incidentService.createOrSyncIncident(userId, dto));
    }

    @GetMapping("/active")
    public ResponseEntity<List<Incident>> getActiveIncidents() {
        return ResponseEntity.ok(incidentService.getActiveIncidents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Incident> getIncident(@PathVariable String id) {
        return ResponseEntity.ok(incidentService.getIncidentById(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Incident> updateIncidentStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "RESOLVED");
        String summary = body.get("summary");
        return ResponseEntity.ok(incidentService.updateStatus(id, status, summary));
    }

    @PostMapping("/{id}/ping")
    public ResponseEntity<CheckIn> sendPing(
            @PathVariable String id,
            @RequestBody PingRequestDto dto,
            Authentication authentication) {
        String senderId = authentication != null ? authentication.getName() : "responder-center";
        return ResponseEntity.ok(checkInService.sendPing(senderId, id, dto));
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<CheckIn> submitCheckIn(
            @PathVariable String id,
            @RequestBody CheckInResponseDto dto,
            Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : "user";
        return ResponseEntity.ok(checkInService.submitResponse(userId, dto));
    }
}
