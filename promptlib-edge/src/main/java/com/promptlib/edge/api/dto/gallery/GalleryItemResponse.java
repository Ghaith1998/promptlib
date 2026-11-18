package com.promptlib.edge.api.dto.gallery;

public record GalleryItemResponse(String id, String title, String description, String categoryId,
                                  int copyCount, String createdAt) {
}
