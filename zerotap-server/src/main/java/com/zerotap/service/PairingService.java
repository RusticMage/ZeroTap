package com.zerotap.service;

import com.zerotap.dto.*;
import com.zerotap.model.EmergencyContactLink;
import com.zerotap.model.PairingCode;
import com.zerotap.model.User;
import com.zerotap.repository.EmergencyContactLinkRepository;
import com.zerotap.repository.PairingCodeRepository;
import com.zerotap.repository.UserRepository;
import com.zerotap.websocket.WebSocketEvent;
import com.zerotap.websocket.WebSocketEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PairingService {

    private final PairingCodeRepository pairingCodeRepository;
    private final EmergencyContactLinkRepository emergencyContactLinkRepository;
    private final UserRepository userRepository;
    private final WebSocketEventPublisher webSocketEventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    public PairingService(
            PairingCodeRepository pairingCodeRepository,
            EmergencyContactLinkRepository emergencyContactLinkRepository,
            UserRepository userRepository,
            WebSocketEventPublisher webSocketEventPublisher) {
        this.pairingCodeRepository = pairingCodeRepository;
        this.emergencyContactLinkRepository = emergencyContactLinkRepository;
        this.userRepository = userRepository;
        this.webSocketEventPublisher = webSocketEventPublisher;
    }

    @Transactional
    public PairingGenerateResponse generatePairingCode(String userId) {
        if (userId == null || userId.isBlank()) {
            userId = "user-device-1";
        }

        // 1. Invalidate any existing unused codes for this user
        List<PairingCode> existingCodes = pairingCodeRepository.findByUserIdAndUsedFalse(userId);
        for (PairingCode existing : existingCodes) {
            existing.setUsed(true);
            pairingCodeRepository.save(existing);
        }

        // 2. Generate a cryptographically random 6-digit number
        int randomNum = secureRandom.nextInt(900000) + 100000;
        String code = String.valueOf(randomNum);

        // 3. Expiration: 10 minutes from now
        Instant now = Instant.now();
        Instant expiresAt = now.plus(10, ChronoUnit.MINUTES);

        PairingCode pairingCode = new PairingCode(
                UUID.randomUUID().toString(),
                userId,
                code,
                expiresAt
        );
        pairingCodeRepository.save(pairingCode);

        // Notify over websocket if listening
        WebSocketEvent event = new WebSocketEvent(
                WebSocketEvent.EventType.PAIRING_CHANGED,
                null,
                userId,
                PairingStatusDto.pending(code, expiresAt, 600)
        );
        webSocketEventPublisher.publishPairingEvent(userId, event);

        return new PairingGenerateResponse(code, expiresAt, 600);
    }

    @Transactional(readOnly = true)
    public PairingStatusDto getPairingStatus(String userId) {
        if (userId == null || userId.isBlank()) {
            userId = "user-device-1";
        }

        // 1. Check if user already has an active emergency contact link
        Optional<EmergencyContactLink> activeLink = emergencyContactLinkRepository.findByUserIdAndActiveTrue(userId);
        if (activeLink.isPresent()) {
            EmergencyContactLink link = activeLink.get();
            return PairingStatusDto.connected(link.getContactName(), link.getContactPhone(), link.getConnectedAt());
        }

        // 2. Check if user has an active, unexpired, unused pairing code
        Instant now = Instant.now();
        List<PairingCode> pendingCodes = pairingCodeRepository.findByUserIdAndUsedFalse(userId);
        for (PairingCode code : pendingCodes) {
            if (!code.isExpired()) {
                long remainingSec = Duration.between(now, code.getExpiresAt()).getSeconds();
                return PairingStatusDto.pending(code.getCode(), code.getExpiresAt(), Math.max(0, remainingSec));
            }
        }

        return PairingStatusDto.notConnected();
    }

    @Transactional
    public PairingClaimResponse claimPairingCode(PairingClaimRequest request) {
        if (request.getCode() == null) {
            throw new IllegalArgumentException("Invalid pairing code format.");
        }

        String cleanCode = request.getCode().replaceAll("[^0-9]", "");
        if (cleanCode.length() != 6) {
            throw new IllegalArgumentException("Invalid pairing code format. Please enter a 6-digit code.");
        }
        Instant now = Instant.now();

        // 1. Check if pairing code was registered in the database
        Optional<PairingCode> existingCodeOpt = pairingCodeRepository.findByCode(cleanCode);

        PairingCode code;
        if (existingCodeOpt.isPresent()) {
            code = existingCodeOpt.get();
            if (code.isUsed()) {
                throw new IllegalArgumentException("This pairing code has already been used. Please generate a fresh code on your phone.");
            }
            if (code.isExpired()) {
                throw new IllegalArgumentException("This pairing code has expired. Please generate a fresh code on your phone.");
            }
        } else {
            // Code was generated offline on the phone: automatically register and link it for primary device
            PairingCode offlineCode = new PairingCode(
                    UUID.randomUUID().toString(),
                    "user-device-1",
                    cleanCode,
                    now.plus(1, ChronoUnit.HOURS)
            );
            code = pairingCodeRepository.save(offlineCode);
        }

        String userId = code.getUserId();

        // Single primary contact per user: deactivate any existing link for this user
        Optional<EmergencyContactLink> currentLink = emergencyContactLinkRepository.findByUserIdAndActiveTrue(userId);
        currentLink.ifPresent(link -> {
            link.setActive(false);
            link.setDisconnectedAt(now);
            emergencyContactLinkRepository.save(link);
        });

        // Mark code as used
        code.setUsed(true);
        code.setUsedAt(now);
        String contactName = request.getContactName() != null && !request.getContactName().isBlank()
                ? request.getContactName().trim() : "Emergency Contact";
        code.setClaimedByContactName(contactName);
        pairingCodeRepository.save(code);

        // Generate cryptographically secure contact secret token for subsequent authorization
        String contactToken = UUID.randomUUID().toString();

        EmergencyContactLink link = new EmergencyContactLink(
                UUID.randomUUID().toString(),
                userId,
                contactToken,
                contactName,
                request.getContactPhone(),
                request.getContactEmail()
        );
        emergencyContactLinkRepository.save(link);

        // Retrieve user's display name
        String userFullName = userRepository.findById(userId)
                .map(User::getFullName)
                .orElse("ZeroTap User (" + userId + ")");

        // Notify phone user via WebSocket that contact is linked
        WebSocketEvent event = new WebSocketEvent(
                WebSocketEvent.EventType.PAIRING_CHANGED,
                null,
                userId,
                PairingStatusDto.connected(contactName, request.getContactPhone(), now)
        );
        webSocketEventPublisher.publishPairingEvent(userId, event);

        return new PairingClaimResponse(true, contactToken, userId, userFullName, "Successfully paired with " + userFullName);
    }

    @Transactional
    public boolean unpair(String userId, String contactToken) {
        Optional<EmergencyContactLink> linkOpt = Optional.empty();

        if (contactToken != null && !contactToken.isBlank()) {
            linkOpt = emergencyContactLinkRepository.findByContactTokenAndActiveTrue(contactToken);
        } else if (userId != null && !userId.isBlank()) {
            linkOpt = emergencyContactLinkRepository.findByUserIdAndActiveTrue(userId);
        }

        if (linkOpt.isPresent()) {
            EmergencyContactLink link = linkOpt.get();
            link.setActive(false);
            link.setDisconnectedAt(Instant.now());
            emergencyContactLinkRepository.save(link);

            // Invalidate any open pairing codes for this user
            List<PairingCode> pendingCodes = pairingCodeRepository.findByUserIdAndUsedFalse(link.getUserId());
            for (PairingCode pc : pendingCodes) {
                pc.setUsed(true);
                pairingCodeRepository.save(pc);
            }

            // Notify phone user
            WebSocketEvent event = new WebSocketEvent(
                    WebSocketEvent.EventType.PAIRING_CHANGED,
                    null,
                    link.getUserId(),
                    PairingStatusDto.notConnected()
            );
            webSocketEventPublisher.publishPairingEvent(link.getUserId(), event);

            // Notify contact
            webSocketEventPublisher.publishContactEvent(link.getContactToken(), event);
            return true;
        }

        return false;
    }

    public Optional<EmergencyContactLink> getActiveLinkByToken(String contactToken) {
        if (contactToken == null || contactToken.isBlank()) return Optional.empty();
        return emergencyContactLinkRepository.findByContactTokenAndActiveTrue(contactToken);
    }
}
