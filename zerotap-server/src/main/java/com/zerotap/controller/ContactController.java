package com.zerotap.controller;

import com.zerotap.dto.ContactMonitoringDto;
import com.zerotap.dto.ContactPingRequestDto;
import com.zerotap.service.ContactService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    /**
     * Emergency Contact Dashboard queries telemetry for the strictly paired phone user.
     * Enforced strictly by X-Contact-Token.
     */
    @GetMapping("/monitor")
    public ResponseEntity<ContactMonitoringDto> getMonitoringData(
            @RequestHeader(value = "X-Contact-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String paramToken) {
        String token = headerToken != null && !headerToken.isBlank() ? headerToken : paramToken;
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(contactService.getMonitoringData(token));
    }

    /**
     * Emergency Contact triggers "ARE YOU OK?" safety check to the linked phone.
     */
    @PostMapping("/ping")
    public ResponseEntity<Map<String, Object>> pingUser(
            @RequestHeader(value = "X-Contact-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String paramToken) {
        String token = headerToken != null && !headerToken.isBlank() ? headerToken : paramToken;
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(contactService.sendPingToUser(token));
    }

    /**
     * Phone User clicks "[I'M OK]" in response to the safety ping.
     */
    @PostMapping("/ping-response")
    public ResponseEntity<Map<String, Object>> respondToPing(@RequestBody ContactPingRequestDto request) {
        return ResponseEntity.ok(contactService.recordPingResponse(request));
    }

    /**
     * Query latest ping information for a user.
     */
    @GetMapping("/ping-status")
    public ResponseEntity<?> getPingStatus(@RequestParam String userId) {
        ContactService.PingInfo pingInfo = contactService.getLatestPingInfo(userId);
        if (pingInfo == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(pingInfo);
    }
}
