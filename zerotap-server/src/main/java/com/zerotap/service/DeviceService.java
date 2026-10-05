package com.zerotap.service;

import com.zerotap.dto.DeviceRegistrationRequest;
import com.zerotap.model.Device;
import com.zerotap.repository.DeviceRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public Device registerDevice(String userId, DeviceRegistrationRequest request) {
        Device device = deviceRepository.findById(request.getDeviceId())
                .orElse(new Device(request.getDeviceId(), userId, request.getDeviceName(), request.getDeviceModel()));

        device.setUserId(userId);
        device.setDeviceName(request.getDeviceName());
        device.setDeviceModel(request.getDeviceModel());
        device.setFcmToken(request.getFcmToken());
        device.setLastSeenAt(Instant.now());

        return deviceRepository.save(device);
    }

    public List<Device> getUserDevices(String userId) {
        return deviceRepository.findByUserId(userId);
    }
}
