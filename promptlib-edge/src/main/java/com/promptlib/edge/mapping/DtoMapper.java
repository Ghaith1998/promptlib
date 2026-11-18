package com.promptlib.edge.mapping;

import com.promptlib.edge.api.dto.account.MeResponse;
import com.promptlib.edge.api.dto.admin.AdminPromptItemResponse;
import com.promptlib.edge.api.dto.gallery.FavoriteItemResponse;
import com.promptlib.edge.api.dto.gallery.GalleryItemResponse;
import com.promptlib.edge.api.dto.library.CategoryResponse;
import com.promptlib.edge.api.dto.library.CollectionResponse;
import com.promptlib.edge.api.dto.library.TagResponse;
import com.promptlib.edge.api.dto.prompt.MessageDto;
import com.promptlib.edge.api.dto.prompt.PromptResponse;
import com.promptlib.edge.api.dto.prompt.VariableDto;
import com.promptlib.edge.api.dto.version.VersionResponse;
import com.promptlib.edge.api.dto.version.VersionSummaryResponse;
import com.promptlib.proto.v1.AdminPromptItem;
import com.promptlib.proto.v1.CategoryReply;
import com.promptlib.proto.v1.CollectionReply;
import com.promptlib.proto.v1.FavoriteItem;
import com.promptlib.proto.v1.GalleryItem;
import com.promptlib.proto.v1.MessageInput;
import com.promptlib.proto.v1.MessageReply;
import com.promptlib.proto.v1.PromptReply;
import com.promptlib.proto.v1.TagReply;
import com.promptlib.proto.v1.UserReply;
import com.promptlib.proto.v1.VariableInput;
import com.promptlib.proto.v1.VariableReply;
import com.promptlib.proto.v1.VersionReply;
import com.promptlib.proto.v1.VersionSummaryReply;

import java.util.List;

/** Proto to REST DTO. The edge owns this shape; core never sees it. */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static PromptResponse prompt(PromptReply p) {
        return new PromptResponse(p.getId(), p.getTitle(), p.getDescription(), p.getVisibility(),
                p.getCategoryId(), p.getLatestVersionId(), p.getPublishedVersionId(),
                p.getForkOfPromptId(), p.getCopyCount(), p.getModerationState(),
                p.getHiddenReason(), p.getCreatedAt(), p.getUpdatedAt());
    }

    public static MeResponse me(UserReply u) {
        return new MeResponse(u.getId(), u.getUsername(), u.getEmail(), u.getDisplayName(), u.getRolesList());
    }

    public static List<MessageInput> messageInputs(List<MessageDto> messages) {
        return messages == null ? List.of() : messages.stream()
                .map(m -> MessageInput.newBuilder()
                        .setRole(m.role()).setContent(m.content()).build())
                .toList();
    }

    public static List<VariableInput> variableInputs(List<VariableDto> variables) {
        return variables == null ? List.of() : variables.stream()
                .map(v -> {
                    VariableInput.Builder b = VariableInput.newBuilder()
                            .setName(v.name())
                            .setType(v.type() == null ? "STRING" : v.type())
                            .setRequired(v.required());
                    if (v.defaultValue() != null) {
                        b.setDefaultValue(v.defaultValue());
                    }
                    if (v.description() != null) {
                        b.setDescription(v.description());
                    }
                    if (v.enumValues() != null) {
                        b.addAllEnumValues(v.enumValues());
                    }
                    return b.build();
                })
                .toList();
    }

    public static VersionSummaryResponse versionSummary(VersionSummaryReply v) {
        return new VersionSummaryResponse(v.getId(), v.getVersionNumber(), v.getChangeNote(), v.getCreatedAt());
    }

    public static VersionResponse version(VersionReply v) {
        return new VersionResponse(v.getId(), v.getPromptId(), v.getVersionNumber(), v.getChangeNote(),
                v.getCreatedAt(), v.getMessagesList().stream().map(DtoMapper::message).toList(),
                v.getVariablesList().stream().map(DtoMapper::variable).toList());
    }

    private static MessageDto message(MessageReply m) {
        return new MessageDto(m.getRole(), m.getContent(), m.getPosition());
    }

    private static VariableDto variable(VariableReply v) {
        return new VariableDto(v.getName(), v.getType(), v.getRequired(), v.getDefaultValue(),
                v.getDescription(), v.getEnumValuesList());
    }

    public static GalleryItemResponse galleryItem(GalleryItem i) {
        return new GalleryItemResponse(i.getId(), i.getTitle(), i.getDescription(), i.getCategoryId(),
                i.getCopyCount(), i.getCreatedAt());
    }

    public static FavoriteItemResponse favorite(FavoriteItem f) {
        return new FavoriteItemResponse(f.getId(), f.getTitle(), f.getDescription(), f.getCategoryId(),
                f.getCopyCount(), f.getCreatedAt());
    }

    public static AdminPromptItemResponse adminPrompt(AdminPromptItem a) {
        return new AdminPromptItemResponse(a.getId(), a.getOwnerId(), a.getOwnerUsername(), a.getTitle(),
                a.getVisibility(), a.getModerationState(), a.getCopyCount(), a.getHiddenReason(),
                a.getCreatedAt());
    }

    public static CollectionResponse collection(CollectionReply c) {
        return new CollectionResponse(c.getId(), c.getName(), c.getColor(), c.getIsDefault());
    }

    public static TagResponse tag(TagReply t) {
        return new TagResponse(t.getId(), t.getName(), t.getColor());
    }

    public static CategoryResponse category(CategoryReply c) {
        return new CategoryResponse(c.getId(), c.getSlug(), c.getName(), c.getColor(), c.getSortOrder());
    }
}
