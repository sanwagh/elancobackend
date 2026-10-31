package com.example.elancobackend.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

// A stable, simple JSON shape for pages (Spring's own Page type is not meant to be serialised directly).
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
