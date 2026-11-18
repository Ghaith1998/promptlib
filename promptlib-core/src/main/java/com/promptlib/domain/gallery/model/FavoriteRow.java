package com.promptlib.domain.gallery.model;

import java.time.Instant;
import java.util.UUID;

public record FavoriteRow(UUID id, String title, String description, UUID categoryId,
                          int copyCount, Instant createdAt) {
}
