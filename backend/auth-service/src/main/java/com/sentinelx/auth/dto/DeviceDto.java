package com.sentinelx.auth.dto;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import com.sentinelx.auth.entity.Device;

/** API contract for a Device (schema whoami.devices). */
public record DeviceDto(
        UUID id,
        UUID userId,
        String deviceFingerprint,
        String deviceType,
        InetAddress ipAddress,
        String userAgent,
        boolean trusted,
        Instant createdAt,
        Instant updatedAt) {

    public static DeviceDto from(Device d) {
        return new DeviceDto(d.getId(), d.getUserId(), d.getDeviceFingerprint(),
                d.getDeviceType(), d.getIpAddress(), d.getUserAgent(), d.isTrusted(),
                d.getCreatedAt(), d.getUpdatedAt());
    }

    public Device toEntity() {
        Device d = new Device();
        d.setUserId(userId);
        d.setDeviceFingerprint(deviceFingerprint);
        d.setDeviceType(deviceType);
        d.setIpAddress(ipAddress);
        d.setUserAgent(userAgent);
        d.setTrusted(trusted);
        return d;
    }
}