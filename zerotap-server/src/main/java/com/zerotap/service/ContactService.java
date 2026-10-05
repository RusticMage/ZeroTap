package com.zerotap.service;

import com.zerotap.dto.ContactMonitoringDto;
import com.zerotap.dto.ContactPingRequestDto;
import com.zerotap.model.EmergencyContactLink;
import com.zerotap.model.Incident;
import com.zerotap.model.LocationRecord;
import com.zerotap.model.User;
import com.zerotap.repository.IncidentRepository;
import com.zerotap.repository.UserRepository;
import com.zerotap.websocket.WebSocketEvent;
import com.zerotap.websocket.WebSocketEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ContactService {

    private final PairingService pairingService;
    private final LocationService locationService;
    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final WebSocketEventPublisher webSocketEventPublisher;

    // Cache latest ping per user: userId -> PingInfo
    public static class PingInfo {
        public String pingId;
        public String contactToken;
        public String contactName;
        public Instant sentAt;
        public Instant respondedAt;
        public String responseStatus; // "PENDING", "SAFE"
        public String responseMessage;
    }

    private final Map<String, PingInfo> userLatestPings = new ConcurrentHashMap<>();

    public ContactService(
            PairingService pairingService,
            LocationService locationService,
            IncidentRepository incidentRepository,
            UserRepository userRepository,
            WebSocketEventPublisher webSocketEventPublisher) {
        this.pairingService = pairingService;
        this.locationService = locationService;
        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.webSocketEventPublisher = webSocketEventPublisher;
    }

    /**
     * Strictly authorized monitoring telemetry.
     * Contact can ONLY access data for their paired user.
     */
    public ContactMonitoringDto getMonitoringData(String contactToken) {
        EmergencyContactLink link = pairingService.getActiveLinkByToken(contactToken)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Invalid or inactive contact authorization token."));

        String userId = link.getUserId();
        ContactMonitoringDto dto = new ContactMonitoringDto();
        dto.setUserId(userId);

        String fullName = userRepository.findById(userId)
                .map(User::getFullName)
                .orElse("ZeroTap User (" + userId + ")");
        dto.setUserFullName(fullName);

        // Location Telemetry & Freshness Evaluation
        Optional<LocationRecord> locOpt = locationService.getLatestLocation(userId);
        Instant now = Instant.now();

        if (locOpt.isEmpty() || (locOpt.get().getLatitude() == 0.0 && locOpt.get().getLongitude() == 0.0)) {
            dto.setStatus("UNAVAILABLE");
            dto.setLatitude(null);
            dto.setLongitude(null);
            dto.setLastLocationUpdate(null);
            dto.setSecondsSinceLastUpdate(null);
        } else {
            LocationRecord loc = locOpt.get();
            dto.setLatitude(loc.getLatitude());
            dto.setLongitude(loc.getLongitude());
            dto.setSpeed(loc.getSpeed());
            dto.setAccuracy((double) loc.getAccuracy());
            dto.setLastLocationUpdate(loc.getTimestamp());

            long secondsSince = Math.max(0, Duration.between(loc.getTimestamp(), now).getSeconds());
            dto.setSecondsSinceLastUpdate(secondsSince);

            if (secondsSince < 15) {
                dto.setStatus("LIVE");
            } else if (secondsSince <= 300) {
                dto.setStatus("STALE");
            } else {
                dto.setStatus("OFFLINE");
            }
        }

        // Active Emergency Check
        List<Incident> activeIncidents = incidentRepository.findByUserIdAndStatus(userId, "ALERTING");
        if (!activeIncidents.isEmpty()) {
            dto.setEmergencyActive(true);
            dto.setActiveIncidentSummary(activeIncidents.get(0).getSummary());
        } else {
            dto.setEmergencyActive(false);
        }

        // Ping Status
        PingInfo ping = userLatestPings.get(userId);
        if (ping != null && ping.respondedAt != null) {
            dto.setLastPingResponse(ping.responseMessage != null ? ping.responseMessage : "Safe");
            dto.setLastPingResponseTime(ping.respondedAt);
        }

        return dto;
    }

    /**
     * Emergency Contact sends "ARE YOU OK?" ping to the paired phone.
     */
    public Map<String, Object> sendPingToUser(String contactToken) {
        EmergencyContactLink link = pairingService.getActiveLinkByToken(contactToken)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Unauthorized emergency contact."));

        String userId = link.getUserId();
        String pingId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        PingInfo pingInfo = new PingInfo();
        pingInfo.pingId = pingId;
        pingInfo.contactToken = contactToken;
        pingInfo.contactName = link.getContactName();
        pingInfo.sentAt = now;
        pingInfo.responseStatus = "PENDING";
        userLatestPings.put(userId, pingInfo);

        // Dispatch via WebSocket directly to phone client
        Map<String, Object> payload = new HashMap<>();
        payload.put("pingId", pingId);
        payload.put("contactName", link.getContactName());
        payload.put("message", "Are you OK? " + link.getContactName() + " is checking on you.");
        payload.put("timeoutSeconds", 30);
        payload.put("timestamp", now.toEpochMilli());

        WebSocketEvent event = new WebSocketEvent(
                WebSocketEvent.EventType.PING_REQUESTED,
                null,
                userId,
                payload
        );
        webSocketEventPublisher.publishPingToUser(userId, event);

        Map<String, Object> res = new HashMap<>();
        res.put("pingId", pingId);
        res.put("sentAt", now);
        res.put("timeoutSeconds", 30);
        res.put("targetUser", userId);
        return res;
    }

    /**
     * Phone user responds "[I'M OK]" to the ping.
     */
    public Map<String, Object> recordPingResponse(ContactPingRequestDto dto) {
        String userId = dto.getUserId();
        if (userId == null || userId.isBlank()) {
            userId = "user-device-1";
        }

        PingInfo ping = userLatestPings.get(userId);
        Instant now = Instant.now();

        if (ping == null) {
            ping = new PingInfo();
            ping.pingId = dto.getPingId() != null ? dto.getPingId() : UUID.randomUUID().toString();
            ping.sentAt = now.minusSeconds(5);
        }

        ping.respondedAt = now;
        ping.responseStatus = dto.getStatus() != null ? dto.getStatus() : "SAFE";
        ping.responseMessage = dto.getMessage() != null && !dto.getMessage().isBlank() ? dto.getMessage() : "I'm OK";
        userLatestPings.put(userId, ping);

        // Notify both phone topic and contact topic
        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("pingId", ping.pingId);
        eventPayload.put("status", ping.responseStatus);
        eventPayload.put("message", ping.responseMessage);
        eventPayload.put("respondedAt", now.toString());
        eventPayload.put("respondedAtMillis", now.toEpochMilli());

        WebSocketEvent event = new WebSocketEvent(
                WebSocketEvent.EventType.PING_RESPONSE,
                null,
                userId,
                eventPayload
        );
        webSocketEventPublisher.publishPingResponse(userId, event);

        if (ping.contactToken != null) {
            webSocketEventPublisher.publishContactEvent(ping.contactToken, event);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("acknowledgedAt", now);
        return res;
    }

    public PingInfo getLatestPingInfo(String userId) {
        return userLatestPings.get(userId);
    }
}
