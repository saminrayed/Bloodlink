package com.bloodlink.model;

import java.time.LocalDateTime;
import java.util.List;

public record Review(long id, long requestId, long reviewerId, String reviewerName, long reviewedId,
                     int rating, List<ReviewTag> tags, String comment, LocalDateTime createdAt) { }
