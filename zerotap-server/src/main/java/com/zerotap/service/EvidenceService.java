package com.zerotap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerotap.dto.EvidenceUploadDto;
import com.zerotap.model.Evidence;
import com.zerotap.repository.EvidenceRepository;
import com.zerotap.websocket.WebSocketEvent;
import com.zerotap.websocket.WebSocketEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EvidenceService {

    private final EvidenceRepository evidenceRepository;
    private final WebSocketEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path storageDirectory = Paths.get(System.getProperty("user.dir"), "uploads", "evidence");

    public EvidenceService(EvidenceRepository evidenceRepository, WebSocketEventPublisher eventPublisher) {
        this.evidenceRepository = evidenceRepository;
        this.eventPublisher = eventPublisher;
        try {
            Files.createDirectories(storageDirectory);
        } catch (IOException ignored) {}
    }

    public Evidence saveEvidence(String userId, EvidenceUploadDto dto) {
        String id = dto.getId() != null && !dto.getId().isBlank() ? dto.getId() : UUID.randomUUID().toString();

        Evidence evidence = evidenceRepository.findById(id).orElseGet(() -> {
            Evidence ev = new Evidence();
            ev.setId(id);
            return ev;
        });

        evidence.setIncidentId(dto.getIncidentId());
        evidence.setUserId(userId);
        evidence.setType(dto.getType() != null ? dto.getType() : "PHOTO");
        evidence.setUri(dto.getUri() != null ? dto.getUri() : "/api/evidence/" + id + "/image");
        evidence.setLatitude(dto.getLatitude());
        evidence.setLongitude(dto.getLongitude());

        // Extract typed vehicle plate and confidence
        String regNum = dto.getRegistrationNumber();
        if ((regNum == null || regNum.isBlank()) && dto.getMetadata() != null) {
            regNum = dto.getMetadata().get("registrationNumber");
        }
        if (regNum != null && !regNum.isBlank()) {
            evidence.setRegistrationNumber(regNum.replaceAll("[^A-Za-z0-9]", "").toUpperCase());
        }

        float conf = dto.getConfidence();
        if (conf <= 0f && dto.getMetadata() != null && dto.getMetadata().containsKey("confidence")) {
            try {
                conf = Float.parseFloat(dto.getMetadata().get("confidence"));
            } catch (Exception ignored) {}
        }
        evidence.setConfidence(conf > 0f ? conf : dto.getRelevanceScore());
        evidence.setRelevanceScore(evidence.getConfidence());

        evidence.setTimestamp(dto.getTimestamp() != null ? dto.getTimestamp() : Instant.now());
        evidence.setSyncedAt(Instant.now());

        try {
            if (dto.getMetadata() != null) {
                evidence.setMetadataJson(objectMapper.writeValueAsString(dto.getMetadata()));
            }
        } catch (Exception e) {
            evidence.setMetadataJson("{}");
        }

        Evidence saved = evidenceRepository.save(evidence);

        // Notify Command Center in real-time
        eventPublisher.publishIncidentEvent(
                dto.getIncidentId() != null ? dto.getIncidentId() : "general",
                new WebSocketEvent(WebSocketEvent.EventType.EVIDENCE_UPLOADED, dto.getIncidentId(), userId, saved)
        );

        return saved;
    }

    public Evidence saveEvidenceWithFile(String userId, EvidenceUploadDto dto, MultipartFile file) throws IOException {
        String id = dto.getId() != null && !dto.getId().isBlank() ? dto.getId() : UUID.randomUUID().toString();
        dto.setId(id);

        if (file != null && !file.isEmpty()) {
            String filename = id + "_" + System.currentTimeMillis() + ".jpg";
            Path targetFile = storageDirectory.resolve(filename);
            file.transferTo(targetFile.toFile());
            dto.setUri("/api/evidence/" + id + "/image");
        }

        Evidence saved = saveEvidence(userId, dto);
        if (file != null && !file.isEmpty()) {
            saved.setImagePath(storageDirectory.resolve(id + "_" + System.currentTimeMillis() + ".jpg").toString());
        }
        return saved;
    }

    public Optional<Resource> getEvidenceImageResource(String evidenceId) {
        try {
            // Find file starting with evidenceId in storageDirectory
            File dir = storageDirectory.toFile();
            if (dir.exists() && dir.isDirectory()) {
                File[] matches = dir.listFiles((d, name) -> name.startsWith(evidenceId));
                if (matches != null && matches.length > 0) {
                    return Optional.of(new FileSystemResource(matches[0]));
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    public List<Evidence> getRecentEvidence() {
        return evidenceRepository.findTop20ByOrderByTimestampDesc();
    }

    public List<Evidence> getEvidenceForIncident(String incidentId) {
        return evidenceRepository.findByIncidentIdOrderByTimestampDesc(incidentId);
    }
}
