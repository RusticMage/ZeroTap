package com.zerotap.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.Instant;

public class LocationUpdateDto {
    private double latitude;
    private double longitude;
    private float accuracy = 10.0f;
    private float speed = 0.0f;
    private float bearing = 0.0f;
    private Instant timestamp = Instant.now();

    public LocationUpdateDto() {}

    public LocationUpdateDto(double latitude, double longitude, float accuracy, float speed, float bearing, Instant timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.speed = speed;
        this.bearing = bearing;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public float getAccuracy() { return accuracy; }
    public void setAccuracy(float accuracy) { this.accuracy = accuracy; }
    public float getSpeed() { return speed; }
    public void setSpeed(float speed) { this.speed = speed; }
    public float getBearing() { return bearing; }
    public void setBearing(float bearing) { this.bearing = bearing; }
    public Instant getTimestamp() { return timestamp; }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    @JsonSetter("timestamp")
    public void setTimestampAny(Object val) {
        if (val instanceof Number num) {
            this.timestamp = Instant.ofEpochMilli(num.longValue());
        } else if (val instanceof String str) {
            try {
                this.timestamp = Instant.parse(str);
            } catch (Exception e) {
                try {
                    this.timestamp = Instant.ofEpochMilli(Long.parseLong(str));
                } catch (Exception ex) {
                    this.timestamp = Instant.now();
                }
            }
        } else {
            this.timestamp = Instant.now();
        }
    }
}
