package com.zerotap.service;

import com.zerotap.dto.LocationUpdateDto;
import com.zerotap.model.LocationRecord;
import com.zerotap.repository.LocationRepository;
import com.zerotap.websocket.WebSocketEvent;
import com.zerotap.websocket.WebSocketEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LocationService {

    private final LocationRepository locationRepository;
    private final WebSocketEventPublisher eventPublisher;
    private final Map<String, LocationRecord> activeDeviceCache = new ConcurrentHashMap<>();

    public LocationService(LocationRepository locationRepository, WebSocketEventPublisher eventPublisher) {
        this.locationRepository = locationRepository;
        this.eventPublisher = eventPublisher;
    }

    public LocationRecord recordLocation(String userId, String deviceId, LocationUpdateDto dto) {
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : 
                                 (deviceId != null && !deviceId.isBlank()) ? deviceId : "user-device-1";
        LocationRecord record = new LocationRecord(
                effectiveUserId,
                deviceId != null ? deviceId : effectiveUserId,
                dto.getLatitude(),
                dto.getLongitude(),
                dto.getAccuracy(),
                dto.getSpeed(),
                dto.getBearing(),
                dto.getTimestamp()
        );

        LocationRecord saved = locationRepository.save(record);
        activeDeviceCache.put(effectiveUserId, saved);

        // Broadcast real-time location update to responders
        eventPublisher.publishUserLocation(effectiveUserId, new WebSocketEvent(
                WebSocketEvent.EventType.LOCATION_UPDATED,
                null,
                effectiveUserId,
                saved
        ));

        return saved;
    }

    public Optional<LocationRecord> getLatestLocation(String userId) {
        LocationRecord cached = activeDeviceCache.get(userId);
        if (cached != null) return Optional.of(cached);
        return locationRepository.findFirstByUserIdOrderByTimestampDesc(userId);
    }

    public List<LocationRecord> getActiveDevicesLocations() {
        return new ArrayList<>(activeDeviceCache.values());
    }

    public List<LocationRecord> getLocationHistory(String userId) {
        return locationRepository.findByUserIdOrderByTimestampDesc(userId);
    }
}
