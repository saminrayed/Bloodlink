package com.bloodlink.model;

import java.time.LocalDateTime;

public record RequestStatusHistoryEntry(
        long id,
        long requestId,
        RequestStatus fromStatus,
        RequestStatus toStatus,
        String changedByName,
        String note,
        LocalDateTime changedAt
) { }
