package com.bloodlink.model;

import java.time.LocalDateTime;

public record Notification(
        long id,
        long userId,
        String title,
        String message,
        String type,
        Long relatedRequestId,
        boolean read,
        LocalDateTime createdAt
) {
    @Override public String toString() {
        return (read ? "" : "● ") + title + " — " + message;
    }
}
