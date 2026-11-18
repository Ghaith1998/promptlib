package com.promptlib.domain.gallery.port;

import com.promptlib.domain.gallery.model.GalleryRow;
import com.promptlib.shared.Cursor;

import java.util.List;
import java.util.UUID;

public interface GalleryQuery {

    List<GalleryRow> newest(UUID categoryId, String query, Cursor cursor, int limit);
}
