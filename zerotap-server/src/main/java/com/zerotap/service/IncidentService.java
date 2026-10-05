package com.zerotap.service;

import com.zerotap.dto.IncidentDto;
import com.zerotap.model.Incident;
import com.zerotap.repository.IncidentRepository;
import com.zerotap.websocket.WebSocketEvent;
import com.zerotap.websocket.WebSocketEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final WebSocketEventPublisher eventPublisher;

    public IncidentService(IncidentRepository incidentRepository, WebSocketEventPublisher eventPublisher) {
        this.incidentRepository = incidentRepository;
        this.eventPublisher = eventPublisher;
    }

    public Incident createOrSyncIncident(String userId, IncidentDto dto) {
        String id = dto.getId() != null && !dto.getId().isBlank() ? dto.getId() : UUID.randomUUID().toString();

        Incident incident = incidentRepository.findById(id).orElse(new Incident());
        incident.setId(id);
        incident.setUserId(userId);
        incident.setDeviceId(dto.getDeviceId());
        incident.setStartTime(dto.getStartTime() != null ? dto.getStartTime() : Instant.now());
        incident.setRiskScore(dto.getRiskScore());
        incident.setRiskState(dto.getRiskState() != null ? dto.getRiskState() : "HIGH_RISK");
        incident.setLatitude(dto.getLatitude());
        incident.setLongitude(dto.getLongitude());
        incident.setStatus(dto.getStatus() != null ? dto.getStatus() : "ACTIVE");
        incident.setSummary(dto.getSummary());
        incident.setUpdatedAt(Instant.now());

        Incident saved = incidentRepository.save(incident);

        // Broadcast to responders
        WebSocketEvent.EventType eventType = "ALERTING".equalsIgnoreCase(saved.getStatus())
                ? WebSocketEvent.EventType.EMERGENCY_STARTED
                : WebSocketEvent.EventType.INCIDENT_UPDATED;

        eventPublisher.publishIncidentEvent(saved.getId(), new WebSocketEvent(eventType, saved.getId(), userId, saved));

        return saved;
    }

    public Incident updateStatus(String incidentId, String newStatus, String resolutionSummary) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        incident.setStatus(newStatus);
        incident.setUpdatedAt(Instant.now());
        if ("RESOLVED".equalsIgnoreCase(newStatus) || "DISMISSED".equalsIgnoreCase(newStatus)) {
            incident.setEndTime(Instant.now());
        }
        if (resolutionSummary != null) {
            incident.setSummary(resolutionSummary);
        }

        Incident saved = incidentRepository.save(incident);

        WebSocketEvent.EventType eventType = "RESOLVED".equalsIgnoreCase(newStatus) || "DISMISSED".equalsIgnoreCase(newStatus)
                ? WebSocketEvent.EventType.EMERGENCY_CANCELLED
                : WebSocketEvent.EventType.INCIDENT_UPDATED;

        eventPublisher.publishIncidentEvent(saved.getId(), new WebSocketEvent(eventType, saved.getId(), saved.getUserId(), saved));

        return saved;
    }

    public List<Incident> getActiveIncidents() {
        return incidentRepository.findByStatusInOrderByStartTimeDesc(Arrays.asList("DETECTED", "ACTIVE", "ALERTING"));
    }

    public List<Incident> getUserIncidents(String userId) {
        return incidentRepository.findByUserIdOrderByStartTimeDesc(userId);
    }

    public Incident getIncidentById(String incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));
    }
}
