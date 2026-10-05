package com.zerotap;

import com.zerotap.dto.*;
import com.zerotap.model.LocationRecord;
import com.zerotap.service.ContactService;
import com.zerotap.service.LocationService;
import com.zerotap.service.PairingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PairingAndContactIntegrationTest {

    @Autowired
    private PairingService pairingService;

    @Autowired
    private ContactService contactService;

    @Autowired
    private LocationService locationService;

    @Test
    public void testCompletePairingMonitoringPingAndUnpairFlow() {
        String testUserId = "test-phone-user-1";

        // 1. Initial status: NOT_CONNECTED
        PairingStatusDto statusBefore = pairingService.getPairingStatus(testUserId);
        assertEquals("NOT_CONNECTED", statusBefore.getStatus());

        // 2. Generate 6-digit cryptographically random code
        PairingGenerateResponse genResponse = pairingService.generatePairingCode(testUserId);
        assertNotNull(genResponse.getCode());
        assertEquals(6, genResponse.getCode().length());
        assertTrue(genResponse.getExpiresInSeconds() > 0);

        // Status is now PENDING
        PairingStatusDto statusPending = pairingService.getPairingStatus(testUserId);
        assertEquals("PENDING", statusPending.getStatus());
        assertEquals(genResponse.getCode(), statusPending.getCode());

        // 3. Claim code with Contact details
        PairingClaimRequest claimReq = new PairingClaimRequest(
                genResponse.getCode(),
                "Sarah (Primary Contact)",
                "+919876543210",
                "sarah@example.com"
        );
        PairingClaimResponse claimRes = pairingService.claimPairingCode(claimReq);
        assertTrue(claimRes.isSuccess());
        assertNotNull(claimRes.getContactToken());
        assertEquals(testUserId, claimRes.getUserId());

        // Status is now CONNECTED
        PairingStatusDto statusConnected = pairingService.getPairingStatus(testUserId);
        assertEquals("CONNECTED", statusConnected.getStatus());
        assertEquals("Sarah (Primary Contact)", statusConnected.getContactName());

        // Code cannot be reused
        assertThrows(IllegalArgumentException.class, () -> pairingService.claimPairingCode(claimReq));

        // 4. Monitoring prior to location update -> UNAVAILABLE
        ContactMonitoringDto monitorInitial = contactService.getMonitoringData(claimRes.getContactToken());
        assertEquals(testUserId, monitorInitial.getUserId());
        assertEquals("UNAVAILABLE", monitorInitial.getStatus());

        // 5. Phone streams location telemetry
        LocationUpdateDto locDto = new LocationUpdateDto();
        locDto.setLatitude(12.9716);
        locDto.setLongitude(80.2437);
        locDto.setSpeed(1.5f);
        locDto.setAccuracy(8.0f);
        locDto.setTimestamp(Instant.now());
        locationService.recordLocation(testUserId, "device-1", locDto);

        // 6. Monitoring should now report LIVE (<15s)
        ContactMonitoringDto monitorLive = contactService.getMonitoringData(claimRes.getContactToken());
        assertEquals("LIVE", monitorLive.getStatus());
        assertEquals(12.9716, monitorLive.getLatitude(), 0.0001);
        assertEquals(80.2437, monitorLive.getLongitude(), 0.0001);
        assertTrue(monitorLive.getSecondsSinceLastUpdate() < 15);

        // 7. Emergency Contact sends "ARE YOU OK?" ping
        var pingRes = contactService.sendPingToUser(claimRes.getContactToken());
        assertNotNull(pingRes.get("pingId"));
        String pingId = (String) pingRes.get("pingId");

        // 8. Phone user responds "[I'M OK]"
        ContactPingRequestDto ack = new ContactPingRequestDto();
        ack.setPingId(pingId);
        ack.setUserId(testUserId);
        ack.setStatus("SAFE");
        ack.setMessage("I'm OK, just walking back home");
        var ackRes = contactService.recordPingResponse(ack);
        assertTrue((Boolean) ackRes.get("success"));

        // 9. Verify monitor returns the latest ping response
        ContactMonitoringDto monitorAfterPing = contactService.getMonitoringData(claimRes.getContactToken());
        assertEquals("I'm OK, just walking back home", monitorAfterPing.getLastPingResponse());
        assertNotNull(monitorAfterPing.getLastPingResponseTime());

        // 10. Contact with invalid token MUST be denied (backend authorization enforcement)
        assertThrows(AccessDeniedException.class, () -> contactService.getMonitoringData("invalid-secret-token-xyz"));

        // 11. Unpair phone
        boolean unpaired = pairingService.unpair(testUserId, null);
        assertTrue(unpaired);

        // Post-unpair: Token is revoked immediately
        assertThrows(AccessDeniedException.class, () -> contactService.getMonitoringData(claimRes.getContactToken()));

        // Status is back to NOT_CONNECTED
        assertEquals("NOT_CONNECTED", pairingService.getPairingStatus(testUserId).getStatus());
    }
}
