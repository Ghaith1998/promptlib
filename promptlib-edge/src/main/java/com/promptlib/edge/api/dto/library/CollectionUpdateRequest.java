package com.promptlib.edge.api.dto.library;

public record CollectionUpdateRequest(String name, String description, String color, Integer sortOrder) {
}
