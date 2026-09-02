package com.bloodlink.model;

import java.time.LocalDateTime;

public record AdminUserRow(long id, String fullName, String email, Role role, String district,
                           boolean approved, boolean active, LocalDateTime createdAt) { }
