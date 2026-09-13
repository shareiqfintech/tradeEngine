package com.example.trading.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Deliberately NOT Spring Data's {@code Page<T>} JSON shape (which includes
 * {@code pageable}, {@code sort}, {@code number} instead of {@code page},
 * etc.) - this is the flat {@code {content, totalElements, totalPages,
 * page, size}} shape the frontend's {@code Page<T>} TypeScript type expects
 * (see frontend/src/types/dto.ts).
 */
public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int page, int size) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                page.getNumber(), page.getSize());
    }
}
