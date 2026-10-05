package com.zerotap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerotap.dto.EvidenceUploadDto;
import com.zerotap.model.Evidence;
import com.zerotap.service.EvidenceService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    private final EvidenceService evidenceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @PostMapping
    public ResponseEntity<Evidence> uploadEvidence(
            @RequestBody EvidenceUploadDto dto,
            Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : "user-device-1";
        return ResponseEntity.ok(evidenceService.saveEvidence(userId, dto));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Evidence> uploadEvidenceWithImage(
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "data", required = false) String dataJson,
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "registrationNumber", required = false) String registrationNumber,
            @RequestParam(value = "confidence", required = false, defaultValue = "1.0") float confidence,
            @RequestParam(value = "latitude", required = false, defaultValue = "0.0") double latitude,
            @RequestParam(value = "longitude", required = false, defaultValue = "0.0") double longitude,
            @RequestParam(value = "incidentId", required = false) String incidentId,
            Authentication authentication) throws IOException {

        String userId = authentication != null ? authentication.getName() : "user-device-1";
        EvidenceUploadDto dto;

        if (dataJson != null && !dataJson.isBlank()) {
            dto = objectMapper.readValue(dataJson, EvidenceUploadDto.class);
        } else {
            dto = new EvidenceUploadDto();
            dto.setId(id);
            dto.setType(type != null ? type : "VEHICLE_PLATE");
            dto.setRegistrationNumber(registrationNumber);
            dto.setConfidence(confidence);
            dto.setLatitude(latitude);
            dto.setLongitude(longitude);
            dto.setIncidentId(incidentId);
        }

        return ResponseEntity.ok(evidenceService.saveEvidenceWithFile(userId, dto, file));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> getEvidenceImage(@PathVariable String id) {
        return evidenceService.getEvidenceImageResource(id)
                .map(resource -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + id + ".jpg\"")
                        .body(resource))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Evidence>> getRecentEvidence() {
        return ResponseEntity.ok(evidenceService.getRecentEvidence());
    }

    @GetMapping("/incident/{incidentId}")
    public ResponseEntity<List<Evidence>> getIncidentEvidence(@PathVariable String incidentId) {
        return ResponseEntity.ok(evidenceService.getEvidenceForIncident(incidentId));
    }
}
