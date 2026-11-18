package com.promptlib.domain.gallery.model;

import java.time.Instant;
import java.util.UUID;

/** Read model for gallery lists. Never carries drafts or private rows. */
public record GalleryItem(UUID id, String title, String description, UUID categoryId,
                          int copyCount, Instant createdAt) {
}
