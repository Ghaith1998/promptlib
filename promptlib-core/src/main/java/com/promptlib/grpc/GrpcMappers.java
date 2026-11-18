package com.promptlib.grpc;

import com.promptlib.domain.admin.model.AdminPromptRow;
import com.promptlib.domain.gallery.model.FavoriteRow;
import com.promptlib.domain.category.model.Category;
import com.promptlib.domain.gallery.model.GalleryRow;
import com.promptlib.domain.library.model.Collection;
import com.promptlib.domain.library.model.Tag;
import com.promptlib.domain.prompt.model.MessageDraft;
import com.promptlib.domain.prompt.model.MessageRole;
import com.promptlib.domain.prompt.model.Prompt;
import com.promptlib.domain.prompt.model.PromptMessage;
import com.promptlib.domain.prompt.model.PromptVersion;
import com.promptlib.domain.prompt.model.VariableDefinition;
import com.promptlib.domain.prompt.model.VariableType;
import com.promptlib.proto.v1.CategoryReply;
import com.promptlib.proto.v1.AdminPromptItem;
import com.promptlib.proto.v1.CollectionReply;
import com.promptlib.proto.v1.FavoriteItem;
import com.promptlib.proto.v1.GalleryItem;
import com.promptlib.proto.v1.MessageInput;
import com.promptlib.proto.v1.MessageReply;
import com.promptlib.proto.v1.PromptReply;
import com.promptlib.proto.v1.TagReply;
import com.promptlib.proto.v1.VariableInput;
import com.promptlib.proto.v1.VariableReply;
import com.promptlib.proto.v1.VersionReply;
import com.promptlib.proto.v1.VersionSummaryReply;
import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.ValidationFailed;

import java.time.Instant;
import java.util.List;

/** Entity to proto mapping. Hand-written on purpose: no reflection, no magic. */
public final class GrpcMappers {

    private GrpcMappers() {
    }

    public static PromptReply prompt(Prompt p) {
        return PromptReply.newBuilder()
                .setId(p.id.toString())
                .setTitle(nz(p.title))
                .setDescription(nz(p.description))
                .setVisibility(p.visibility.name())
                .setCategoryId(nz(p.categoryId))
                .setLatestVersionId(nz(p.latestVersionId))
                .setPublishedVersionId(nz(p.publishedVersionId))
                .setForkOfPromptId(nz(p.forkOfPromptId))
                .setCopyCount(p.copyCount)
                .setModerationState(p.moderationState.name())
                .setHiddenReason(nz(p.hiddenReason))
                .setCreatedAt(iso(p.createdAt))
                .setUpdatedAt(iso(p.updatedAt))
                .build();
    }

    public static CategoryReply category(Category c) {
        return CategoryReply.newBuilder()
                .setId(c.id.toString())
                .setSlug(nz(c.slug))
                .setName(nz(c.name))
                .setColor(nz(c.color))
                .setSortOrder(c.sortOrder)
                .setActive(c.active)
                .build();
    }

    public static CollectionReply collection(Collection c) {
        return CollectionReply.newBuilder()
                .setId(c.id.toString())
                .setName(nz(c.name))
                .setColor(nz(c.color))
                .setIsDefault(c.isDefault)
                .setDescription(nz(c.description))
                .setSortOrder(c.sortOrder)
                .build();
    }

    public static FavoriteItem favorite(FavoriteRow f) {
        return FavoriteItem.newBuilder()
                .setId(f.id().toString())
                .setTitle(nz(f.title()))
                .setDescription(nz(f.description()))
                .setCategoryId(nz(f.categoryId()))
                .setCopyCount(f.copyCount())
                .setCreatedAt(iso(f.createdAt()))
                .build();
    }

    public static AdminPromptItem adminPrompt(AdminPromptRow r) {
        return AdminPromptItem.newBuilder()
                .setId(r.id().toString())
                .setOwnerId(r.ownerId().toString())
                .setOwnerUsername(nz(r.ownerUsername()))
                .setTitle(nz(r.title()))
                .setVisibility(nz(r.visibility()))
                .setModerationState(nz(r.moderationState()))
                .setCopyCount(r.copyCount())
                .setHiddenReason(nz(r.hiddenReason()))
                .setCreatedAt(iso(r.createdAt()))
                .build();
    }

    public static TagReply tag(Tag t) {
        return TagReply.newBuilder()
                .setId(t.id.toString())
                .setName(nz(t.name))
                .setColor(nz(t.color))
                .build();
    }

    public static GalleryItem galleryItem(GalleryRow i) {
        return GalleryItem.newBuilder()
                .setId(i.id().toString())
                .setTitle(nz(i.title()))
                .setDescription(nz(i.description()))
                .setCategoryId(nz(i.categoryId()))
                .setCopyCount(i.copyCount())
                .setCreatedAt(iso(i.createdAt()))
                .build();
    }

    public static VersionSummaryReply versionSummary(PromptVersion v) {
        return VersionSummaryReply.newBuilder()
                .setId(v.id.toString())
                .setVersionNumber(v.versionNumber)
                .setChangeNote(nz(v.changeNote))
                .setCreatedAt(iso(v.createdAt))
                .build();
    }

    public static VersionReply version(PromptVersion v, List<PromptMessage> messages) {
        VersionReply.Builder b = VersionReply.newBuilder()
                .setId(v.id.toString())
                .setPromptId(v.promptId.toString())
                .setVersionNumber(v.versionNumber)
                .setChangeNote(nz(v.changeNote))
                .setCreatedAt(iso(v.createdAt));
        for (PromptMessage m : messages) {
            b.addMessages(MessageReply.newBuilder()
                    .setRole(m.role.name())
                    .setPosition(m.position)
                    .setContent(nz(m.content))
                    .build());
        }
        for (VariableDefinition d : v.variables) {
            b.addVariables(variable(d));
        }
        return b.build();
    }

    public static VariableReply variable(VariableDefinition d) {
        return VariableReply.newBuilder()
                .setName(nz(d.name()))
                .setType(d.type().name())
                .setRequired(d.required())
                .setDefaultValue(nz(d.defaultValue()))
                .setDescription(nz(d.description()))
                .addAllEnumValues(d.enumValues())
                .build();
    }

    public static List<MessageDraft> messages(List<MessageInput> input) {
        return input.stream()
                .map(m -> new MessageDraft(role(m.getRole()), m.getContent()))
                .toList();
    }

    public static List<VariableDefinition> variables(List<VariableInput> input) {
        return input.stream()
                .map(v -> new VariableDefinition(v.getName(), type(v.getType()), v.getRequired(),
                        blankToNull(v.getDefaultValue()), blankToNull(v.getDescription()), v.getEnumValuesList()))
                .toList();
    }

    public static MessageRole role(String value) {
        try {
            return MessageRole.valueOf(value.toUpperCase());
        } catch (RuntimeException e) {
            throw new ValidationFailed(ProblemCode.VALIDATION_FAILED, "unknown role " + value);
        }
    }

    public static VariableType type(String value) {
        try {
            return VariableType.valueOf(value.toUpperCase());
        } catch (RuntimeException e) {
            throw new ValidationFailed(ProblemCode.VARIABLE_MISMATCH, "unknown variable type " + value);
        }
    }

    public static String iso(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    public static String nz(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
