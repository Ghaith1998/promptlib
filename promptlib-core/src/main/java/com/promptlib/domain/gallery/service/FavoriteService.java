package com.promptlib.domain.gallery.service;

import com.promptlib.domain.gallery.model.FavoriteRow;
import com.promptlib.domain.gallery.port.FavoriteQuery;
import com.promptlib.domain.gallery.port.FavoriteRepository;
import com.promptlib.shared.CallerContext;
import com.promptlib.shared.port.PromptLookup;
import com.promptlib.shared.error.NotFound;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Transactional
public class FavoriteService {

    private final FavoriteRepository favorites;
    private final FavoriteQuery favoriteQuery;
    private final PromptLookup prompts;
    private final Clock clock;

    @Inject
    public FavoriteService(FavoriteRepository favorites, FavoriteQuery favoriteQuery,
                           PromptLookup prompts, Clock clock) {
        this.favorites = favorites;
        this.favoriteQuery = favoriteQuery;
        this.prompts = prompts;
        this.clock = clock;
    }

    @Transactional
    public void add(CallerContext caller, UUID promptId) {
        if (!prompts.existsVisible(promptId, caller.userId())) {
            throw new NotFound();
        }
        if (!favorites.exists(caller.userId(), promptId)) {
            favorites.add(caller.userId(), promptId, clock.instant());
        }
    }

    @Transactional
    public void remove(CallerContext caller, UUID promptId) {
        favorites.remove(caller.userId(), promptId);
    }

    public List<FavoriteRow> list(CallerContext caller) {
        return favoriteQuery.list(caller.userId());
    }
}
