package com.promptlib.domain.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.promptlib.domain.gallery.port.FavoriteRepository;
import com.promptlib.domain.library.model.Collection;
import com.promptlib.domain.library.model.Tag;
import com.promptlib.domain.library.port.CollectionRepository;
import com.promptlib.domain.library.port.TagRepository;
import com.promptlib.domain.prompt.model.Prompt;
import com.promptlib.domain.prompt.model.PromptMessage;
import com.promptlib.domain.prompt.model.PromptVersion;
import com.promptlib.domain.prompt.port.PromptRepository;
import com.promptlib.domain.prompt.port.VersionRepository;
import com.promptlib.shared.CallerContext;
import com.promptlib.shared.Cursor;
import com.promptlib.shared.error.NotFound;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only projection of the caller's library. One consistent snapshot, streamed by the edge.
 * Cross-domain reads go through ports, never through entities of other aggregates.
 */
@ApplicationScoped
public class ExportService {

    public static final String SCHEMA_VERSION = "1.0";
    private static final int CHUNK = 30_000;
    private static final int PAGE = 200;

    private final PromptRepository prompts;
    private final VersionRepository versions;
    private final CollectionRepository collections;
    private final TagRepository tags;
    private final FavoriteRepository favorites;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Inject
    public ExportService(PromptRepository prompts, VersionRepository versions, CollectionRepository collections,
                         TagRepository tags, FavoriteRepository favorites, ObjectMapper mapper, Clock clock) {
        this.prompts = prompts;
        this.versions = versions;
        this.collections = collections;
        this.tags = tags;
        this.favorites = favorites;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public List<String> exportLibrary(CallerContext caller) {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("exportedAt", clock.instant().toString());
        root.put("exportedBy", caller.userId().toString());

        Map<UUID, List<UUID>> byCollection = new HashMap<>();
        ArrayNode collectionsNode = root.putArray("collections");
        for (Collection collection : collections.listOwned(caller.userId())) {
            List<UUID> ids = collections.promptIds(collection.id);
            byCollection.put(collection.id, ids);
            ObjectNode c = collectionsNode.addObject();
            c.put("id", collection.id.toString());
            c.put("name", collection.name);
            c.put("color", collection.color);
            c.put("isDefault", collection.isDefault);
            c.put("sortOrder", collection.sortOrder);
            ArrayNode idsNode = c.putArray("promptIds");
            ids.forEach(id -> idsNode.add(id.toString()));
        }

        ArrayNode tagsNode = root.putArray("tags");
        for (Tag tag : tags.listOwned(caller.userId())) {
            ObjectNode t = tagsNode.addObject();
            t.put("id", tag.id.toString());
            t.put("name", tag.name);
            t.put("color", tag.color);
        }

        ArrayNode favoritesNode = root.putArray("favorites");
        favorites.list(caller.userId()).forEach(id -> favoritesNode.add(id.toString()));

        ArrayNode promptsNode = root.putArray("prompts");
        String cursor = null;
        while (true) {
            var page = prompts.pageOwned(caller.userId(), cursor == null ? null : Cursor.decode(cursor),
                    PAGE + 1);
            boolean hasMore = page.size() > PAGE;
            List<Prompt> batch = hasMore ? page.subList(0, PAGE) : page;
            for (Prompt prompt : batch) {
                promptsNode.add(promptNode(prompt, caller, byCollection));
            }
            if (!hasMore) {
                break;
            }
            Prompt last = batch.get(batch.size() - 1);
            cursor = Cursor.of(last.createdAt, last.id).encode();
        }

        return chunk(root.toPrettyString());
    }

    @Transactional
    public List<String> exportMarkdown(CallerContext caller, UUID promptId) {
        Prompt prompt = prompts.findOwned(promptId, caller.userId()).orElseThrow(NotFound::new);
        PromptVersion version = prompt.latestVersionId == null ? null
                : versions.byId(prompt.latestVersionId).orElse(null);

        StringBuilder md = new StringBuilder();
        md.append("# ").append(prompt.title).append("\n\n");
        if (prompt.description != null && !prompt.description.isBlank()) {
            md.append(prompt.description).append("\n\n");
        }
        md.append("- visibility: ").append(prompt.visibility).append('\n');
        md.append("- category: ").append(prompt.categoryId == null ? "-" : prompt.categoryId).append('\n');
        md.append("- version: ").append(version == null ? "-" : version.versionNumber).append("\n\n");

        if (version != null) {
            if (!version.variables.isEmpty()) {
                md.append("## Variables\n\n");
                version.variables.forEach(v -> md.append("- `").append(v.name()).append("` (")
                        .append(v.type()).append(v.required() ? ", required" : "").append(")\n"));
                md.append('\n');
            }
            for (PromptMessage message : versions.messages(version.id)) {
                md.append("### ").append(message.role).append("\n\n");
                md.append(message.content).append("\n\n");
            }
        }
        return chunk(md.toString());
    }

    private ObjectNode promptNode(Prompt prompt, CallerContext caller, Map<UUID, List<UUID>> byCollection) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", prompt.id.toString());
        node.put("title", prompt.title);
        node.put("description", prompt.description);
        node.put("visibility", prompt.visibility.name());
        node.put("categoryId", prompt.categoryId == null ? null : prompt.categoryId.toString());
        node.put("forkOfPromptId", prompt.forkOfPromptId == null ? null : prompt.forkOfPromptId.toString());
        node.put("copyCount", prompt.copyCount);
        node.put("createdAt", prompt.createdAt.toString());
        node.put("updatedAt", prompt.updatedAt.toString());
        node.put("latestVersionId", prompt.latestVersionId == null ? null : prompt.latestVersionId.toString());
        node.put("publishedVersionId",
                prompt.publishedVersionId == null ? null : prompt.publishedVersionId.toString());

        ArrayNode versionsNode = node.putArray("versions");
        for (PromptVersion version : versions.list(prompt.id)) {
            ObjectNode v = versionsNode.addObject();
            v.put("versionNumber", version.versionNumber);
            v.put("changeNote", version.changeNote);
            v.put("createdAt", version.createdAt.toString());
            v.set("variables", mapper.valueToTree(version.variables));
            ArrayNode messagesNode = v.putArray("messages");
            for (PromptMessage message : versions.messages(version.id)) {
                ObjectNode m = messagesNode.addObject();
                m.put("role", message.role.name());
                m.put("position", message.position);
                m.put("content", message.content);
            }
        }

        ArrayNode tagNames = node.putArray("tags");
        tags.tagsOf(prompt.id).forEach(tag -> tagNames.add(tag.name));

        ArrayNode collectionIds = node.putArray("collectionIds");
        byCollection.forEach((collectionId, promptIds) -> {
            if (promptIds.contains(prompt.id)) {
                collectionIds.add(collectionId.toString());
            }
        });
        return node;
    }

    private List<String> chunk(String payload) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < payload.length(); i += CHUNK) {
            chunks.add(payload.substring(i, Math.min(payload.length(), i + CHUNK)));
        }
        if (chunks.isEmpty()) {
            chunks.add("");
        }
        return chunks;
    }
}
