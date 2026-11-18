package com.promptlib.edge.api.dto.gallery;

public record FavoriteItemResponse(String id, String title, String description, String categoryId,
                                   int copyCount, String createdAt) {
}
