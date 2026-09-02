package com.bloodlink.model;

import java.time.LocalDateTime;

public record AuditEntry(long id, Long actorUserId, String actorName, String action,
                         String entityType, Long entityId, String details, LocalDateTime createdAt) { }
