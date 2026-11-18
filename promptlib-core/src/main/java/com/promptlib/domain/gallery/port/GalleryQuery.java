package com.promptlib.domain.gallery.port;

import com.promptlib.domain.gallery.model.GalleryItem;
import com.promptlib.shared.Cursor;

import java.util.List;
import java.util.UUID;

public interface GalleryQuery {

    List<GalleryItem> newest(UUID categoryId, Cursor cursor, int limit);
}
