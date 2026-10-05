package com.zerotap;

import com.zerotap.dto.*;
import com.zerotap.model.Incident;
import com.zerotap.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ZerotapServerApplicationTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private VertexAiService vertexAiService;

    @Autowired
    private SafeSpaceService safeSpaceService;

    @Test
    void contextLoads() {
    }

    @Test
    void testAuthWorkflow() {
        String email = "responder-" + System.currentTimeMillis() + "@zerotap.org";
        RegisterRequest registerReq = new RegisterRequest(email, "StrongPassword123!", "Officer Raj", "RESPONDER");
        AuthResponse registerResp = authService.register(registerReq);

        assertNotNull(registerResp.getToken());
        assertEquals(email, registerResp.getEmail());
        assertEquals("RESPONDER", registerResp.getRole());

        AuthResponse loginResp = authService.login(new AuthRequest(email, "StrongPassword123!"));
        assertNotNull(loginResp.getToken());
        assertEquals(registerResp.getUserId(), loginResp.getUserId());
    }

    @Test
    void testIncidentLifecycle() {
        IncidentDto dto = new IncidentDto();
        dto.setRiskScore(0.85f);
        dto.setRiskState("HIGH_RISK");
        dto.setStatus("ACTIVE");
        dto.setLatitude(13.0827);
        dto.setLongitude(80.2707);

        Incident incident = incidentService.createOrSyncIncident("user-test-1", dto);
        assertNotNull(incident.getId());
        assertEquals("ACTIVE", incident.getStatus());

        Incident resolved = incidentService.updateStatus(incident.getId(), "RESOLVED", "False alarm - user safe");
        assertEquals("RESOLVED", resolved.getStatus());
        assertNotNull(resolved.getEndTime());
    }

    @Test
    void testVertexAiAudioAndVisionInference() {
        AiAudioAnalysisRequest audioReq = new AiAudioAnalysisRequest(System.currentTimeMillis(), 82.5f, 1000L);
        AiAudioAnalysisResponse audioResp = vertexAiService.analyzeAudio(audioReq);
        assertNotNull(audioResp);
        assertTrue(audioResp.isDistressLikePattern());

        AiVehicleImageRequest imgReq = new AiVehicleImageRequest("img-1", "file:///cabs/TN07CB1234.jpg", System.currentTimeMillis(), 13.0, 80.2);
        AiVehicleImageResponse imgResp = vertexAiService.analyzeVehicleImage(imgReq);
        assertNotNull(imgResp);
        assertEquals("TN07CB1234", imgResp.getPlateNumber());
    }
}
