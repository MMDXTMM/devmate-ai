package com.devmate.common.api;

import java.util.List;

public record PageResponse<T>(
        long page,
        long size,
        long total,
        long pages,
        List<T> items
) {
}
