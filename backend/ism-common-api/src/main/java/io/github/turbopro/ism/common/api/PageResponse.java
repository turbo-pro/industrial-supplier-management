package io.github.turbopro.ism.common.api;

import java.util.List;

public record PageResponse<T>(List<T> items, long page, long pageSize, long total) {

    public PageResponse {
        items = items == null ? List.of() : List.copyOf(items);
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be at least 1");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
    }
}
