package com.credchain.common.api;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable JSON shape for paginated results.
 * (Returning Spring's Page object directly is discouraged; its JSON format can change.)
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}