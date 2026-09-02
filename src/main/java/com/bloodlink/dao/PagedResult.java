package com.bloodlink.dao;

import java.util.List;

/**
 * A single page of results plus enough metadata to render pager controls,
 * so a query never has to load every matching row into memory (and every
 * matching row across the network into the JavaFX table) just to show one
 * screenful. Used by the admin screen's user/request search, which is the
 * one place in the app that can plausibly face 1000+ rows.
 */
public record PagedResult<T>(List<T> items, int page, int pageSize, long totalCount) {
    public int totalPages() {
        return pageSize <= 0 ? 1 : (int) Math.max(1, Math.ceil((double) totalCount / pageSize));
    }

    public boolean hasPrevious() { return page > 1; }

    public boolean hasNext() { return page < totalPages(); }
}
