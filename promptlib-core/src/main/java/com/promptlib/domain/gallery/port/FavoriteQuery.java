package com.promptlib.domain.gallery.port;

import com.promptlib.domain.gallery.model.FavoriteRow;

import java.util.List;
import java.util.UUID;

public interface FavoriteQuery {

    List<FavoriteRow> list(UUID userId);
}
