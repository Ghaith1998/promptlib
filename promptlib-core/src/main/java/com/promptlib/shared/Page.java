package com.promptlib.shared;

import java.util.List;

/** Cursor page. There is deliberately no total count. */
public record Page<T>(List<T> items, String nextCursor) {

    public static <T> Page<T> of(List<T> items, String nextCursor) {
        return new Page<>(List.copyOf(items), nextCursor);
    }
}
