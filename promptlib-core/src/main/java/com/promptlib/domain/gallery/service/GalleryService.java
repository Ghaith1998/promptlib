package com.promptlib.domain.gallery.service;

import com.promptlib.domain.gallery.model.GalleryItem;
import com.promptlib.domain.gallery.port.GalleryQuery;
import com.promptlib.shared.CallerContext;
import com.promptlib.shared.Cursor;
import com.promptlib.shared.Page;
import com.promptlib.shared.port.PromptForking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class GalleryService {

    private final GalleryQuery query;
    private final PromptForking forking;

    @Inject
    public GalleryService(GalleryQuery query, PromptForking forking) {
        this.query = query;
        this.forking = forking;
    }

    public Page<GalleryItem> browse(UUID categoryId, String cursor, int limit) {
        int size = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 100));
        Cursor decoded = cursor == null || cursor.isBlank() ? null : Cursor.decode(cursor);

        List<GalleryItem> rows = query.newest(categoryId, decoded, size + 1);
        boolean hasMore = rows.size() > size;
        List<GalleryItem> items = hasMore ? rows.subList(0, size) : rows;
        String next = null;
        if (hasMore) {
            GalleryItem last = items.get(items.size() - 1);
            next = Cursor.of(last.createdAt(), last.id()).encode();
        }
        return Page.of(items, next);
    }

    public UUID fork(UUID sourcePromptId, CallerContext caller) {
        return forking.fork(sourcePromptId, caller);
    }
}
