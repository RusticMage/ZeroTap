package com.zerotap.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebSocketEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishIncidentEvent(String incidentId, WebSocketEvent event) {
        messagingTemplate.convertAndSend("/topic/incidents/" + incidentId, event);
        messagingTemplate.convertAndSend("/topic/responders", event); // Broad responder channel
    }

    public void publishUserLocation(String userId, WebSocketEvent event) {
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/location", event);
        messagingTemplate.convertAndSend("/topic/responders/locations", event);
    }

    public void publishPingToUser(String userId, WebSocketEvent event) {
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/ping", event);
    }

    public void publishPingResponse(String userId, WebSocketEvent event) {
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/ping-response", event);
    }

    public void publishPairingEvent(String userId, WebSocketEvent event) {
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/pairing", event);
    }

    public void publishContactEvent(String contactToken, WebSocketEvent event) {
        messagingTemplate.convertAndSend("/topic/contacts/" + contactToken, event);
    }
}
