package com.bloodlink.model;

import java.time.LocalDateTime;

public abstract class User {
    private long id;
    private String fullName;
    private String email;
    private String phone;
    private String district;
    private String address;
    private final Role role;
    private boolean approved;
    private boolean active;
    private LocalDateTime createdAt;

    protected User(long id, String fullName, String email, String phone, String district,
                   String address, Role role, boolean approved, boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.district = district;
        this.address = address;
        this.role = role;
        this.approved = approved;
        this.active = active;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Role getRole() { return role; }
    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
