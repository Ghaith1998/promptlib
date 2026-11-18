package com.promptlib.domain.gallery.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FavoriteRepository {

    boolean exists(UUID userId, UUID promptId);

    void add(UUID userId, UUID promptId, Instant now);

    void remove(UUID userId, UUID promptId);

    List<UUID> list(UUID userId);
}
