package com.zerotap.service;

import com.zerotap.model.SafeSpace;
import com.zerotap.repository.SafeSpaceRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SafeSpaceService {

    private final SafeSpaceRepository safeSpaceRepository;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SafeSpaceService.class);

    public SafeSpaceService(SafeSpaceRepository safeSpaceRepository) {
        this.safeSpaceRepository = safeSpaceRepository;
    }

    @jakarta.annotation.PostConstruct
    public void initSafeSpaces() {
        try {
            if (safeSpaceRepository.count() == 0) {
                org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource("safe_spaces_tamilnadu.json");
                if (resource.exists()) {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(resource.getInputStream());
                    com.fasterxml.jackson.databind.JsonNode array = root.get("safe_spaces");
                    if (array != null && array.isArray()) {
                        List<SafeSpace> list = new java.util.ArrayList<>();
                        for (com.fasterxml.jackson.databind.JsonNode node : array) {
                            SafeSpace space = new SafeSpace(
                                    node.path("id").asText(),
                                    node.path("name").asText(),
                                    node.path("type").asText("POLICE"),
                                    node.path("latitude").asDouble(),
                                    node.path("longitude").asDouble(),
                                    node.path("address").asText(""),
                                    node.path("phone").asText(""),
                                    node.path("verified").asBoolean(true),
                                    node.path("priority").asInt(1)
                            );
                            list.add(space);
                        }
                        safeSpaceRepository.saveAll(list);
                        log.info("Initialized {} Tamil Nadu safe spaces into database", list.size());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to seed initial Tamil Nadu safe spaces", e);
        }
    }

    public List<SafeSpace> getAllSafeSpaces(String type) {
        if (type != null && !type.isBlank()) {
            return safeSpaceRepository.findByTypeOrderByPriorityDesc(type.toUpperCase());
        }
        return safeSpaceRepository.findAll();
    }

    public List<SafeSpaceDistance> getNearbySafeSpaces(double latitude, double longitude, double radiusMeters, String type) {
        List<SafeSpace> spaces = getAllSafeSpaces(type);

        return spaces.stream()
                .map(s -> new SafeSpaceDistance(s, calculateDistanceMeters(latitude, longitude, s.getLatitude(), s.getLongitude())))
                .filter(sd -> sd.distanceMeters() <= radiusMeters)
                .sorted(Comparator.comparingDouble(SafeSpaceDistance::distanceMeters))
                .collect(Collectors.toList());
    }

    private double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Radius of Earth in meters
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public record SafeSpaceDistance(SafeSpace safeSpace, double distanceMeters) {}
}
