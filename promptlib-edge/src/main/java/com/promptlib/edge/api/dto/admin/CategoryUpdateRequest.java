package com.promptlib.edge.api.dto.admin;

public record CategoryUpdateRequest(String name, String description, String color, Integer sortOrder) {
}
