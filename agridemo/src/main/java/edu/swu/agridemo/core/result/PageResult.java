package edu.swu.agridemo.core.result;

import java.util.List;

public record PageResult<T>(
    List<T> records,
    Long total,
    Integer page,
    Integer size,
    Integer totalPages
) {
    public static <T> PageResult<T> of(List<T> records, Long total, Integer page, Integer size) {
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResult<>(records, total, page, size, totalPages);
    }
}
