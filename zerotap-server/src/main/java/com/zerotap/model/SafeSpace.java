package com.zerotap.model;

import jakarta.persistence.*;

@Entity
@Table(name = "safe_spaces", indexes = {
    @Index(name = "idx_safespace_type", columnList = "type")
})
public class SafeSpace {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    private String address;

    private String phone;

    private boolean verified;

    private int priority;

    public SafeSpace() {}

    public SafeSpace(String id, String name, String type, double latitude, double longitude, String address, String phone, boolean verified, int priority) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.phone = phone;
        this.verified = verified;
        this.priority = priority;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
}
