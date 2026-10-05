package com.zerotap.controller;

import com.zerotap.dto.*;
import com.zerotap.service.PairingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pairing")
public class PairingController {

    private final PairingService pairingService;

    public PairingController(PairingService pairingService) {
        this.pairingService = pairingService;
    }

    /**
     * Phone requests a short-lived, single-use, 6-digit cryptographically random pairing code.
     */
    @PostMapping("/generate")
    public ResponseEntity<PairingGenerateResponse> generateCode(
            @RequestParam(required = false) String userId,
            Authentication authentication) {
        String effectiveUser = authentication != null ? authentication.getName() :
                (userId != null && !userId.isBlank()) ? userId : "user-device-1";
        return ResponseEntity.ok(pairingService.generatePairingCode(effectiveUser));
    }

    /**
     * Check current pairing status for a phone user.
     */
    @GetMapping("/status")
    public ResponseEntity<PairingStatusDto> getStatus(
            @RequestParam(required = false) String userId,
            Authentication authentication) {
        String effectiveUser = authentication != null ? authentication.getName() :
                (userId != null && !userId.isBlank()) ? userId : "user-device-1";
        return ResponseEntity.ok(pairingService.getPairingStatus(effectiveUser));
    }

    /**
     * Emergency Contact claims the 6-digit code on the web dashboard to establish secure 1:1 pairing.
     */
    @PostMapping("/claim")
    public ResponseEntity<PairingClaimResponse> claimCode(@RequestBody PairingClaimRequest request) {
        return ResponseEntity.ok(pairingService.claimPairingCode(request));
    }

    /**
     * Unpair / Disconnect emergency contact.
     * Can be invoked by phone user or contact.
     */
    @PostMapping("/unpair")
    public ResponseEntity<Map<String, Object>> unpair(
            @RequestParam(required = false) String userId,
            @RequestHeader(value = "X-Contact-Token", required = false) String contactToken,
            Authentication authentication) {
        String effectiveUser = authentication != null ? authentication.getName() : userId;
        boolean success = pairingService.unpair(effectiveUser, contactToken);
        return ResponseEntity.ok(Map.of("success", success, "message", success ? "Successfully unpaired." : "No active link found."));
    }
}
