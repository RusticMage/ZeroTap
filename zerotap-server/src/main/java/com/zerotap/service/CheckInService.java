package com.zerotap.service;

import com.zerotap.dto.CheckInResponseDto;
import com.zerotap.dto.PingRequestDto;
import com.zerotap.model.CheckIn;
import com.zerotap.repository.CheckInRepository;
import com.zerotap.websocket.WebSocketEvent;
import com.zerotap.websocket.WebSocketEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CheckInService {

    private final CheckInRepository checkInRepository;
    private final WebSocketEventPublisher eventPublisher;

    public CheckInService(CheckInRepository checkInRepository, WebSocketEventPublisher eventPublisher) {
        this.checkInRepository = checkInRepository;
        this.eventPublisher = eventPublisher;
    }

    public CheckIn sendPing(String senderId, String incidentId, PingRequestDto dto) {
        String checkInId = UUID.randomUUID().toString();
        CheckIn checkIn = new CheckIn();
        checkIn.setId(checkInId);
        checkIn.setIncidentId(incidentId);
        checkIn.setSenderId(senderId);
        checkIn.setRecipientId(dto.getRecipientId());
        checkIn.setTimestamp(Instant.now());
        checkIn.setRequestStatus("PENDING");

        CheckIn saved = checkInRepository.save(checkIn);

        // Notify client via WebSocket
        eventPublisher.publishPingToUser(dto.getRecipientId(), new WebSocketEvent(
                WebSocketEvent.EventType.PING_REQUESTED,
                incidentId,
                senderId,
                saved
        ));

        return saved;
    }

    public CheckIn submitResponse(String userId, CheckInResponseDto dto) {
        CheckIn checkIn = checkInRepository.findById(dto.getCheckInId())
                .orElseThrow(() -> new IllegalArgumentException("CheckIn not found: " + dto.getCheckInId()));

        checkIn.setResponse(dto.getResponse());
        checkIn.setResponseTimestamp(Instant.now());
        checkIn.setRequestStatus("DELIVERED");

        CheckIn saved = checkInRepository.save(checkIn);

        // Broadcast to responders
        eventPublisher.publishIncidentEvent(saved.getIncidentId(), new WebSocketEvent(
                WebSocketEvent.EventType.CHECK_IN_RESPONSE,
                saved.getIncidentId(),
                userId,
                saved
        ));

        return saved;
    }

    public List<CheckIn> getIncidentCheckIns(String incidentId) {
        return checkInRepository.findByIncidentIdOrderByTimestampDesc(incidentId);
    }
}
