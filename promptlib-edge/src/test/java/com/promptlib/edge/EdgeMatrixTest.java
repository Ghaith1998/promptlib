package com.promptlib.edge;

import com.promptlib.edge.client.AccountClient;
import com.promptlib.edge.client.AdminClient;
import com.promptlib.edge.client.CategoryClient;
import com.promptlib.edge.client.ExportClient;
import com.promptlib.edge.client.GalleryClient;
import com.promptlib.edge.client.LibraryClient;
import com.promptlib.edge.client.PromptClient;
import com.promptlib.edge.client.VersionClient;
import com.promptlib.proto.v1.AdminPromptItem;
import com.promptlib.proto.v1.BrowseReply;
import com.promptlib.proto.v1.CategoryReply;
import com.promptlib.proto.v1.CollectionReply;
import com.promptlib.proto.v1.ExportChunk;
import com.promptlib.proto.v1.FavoriteItem;
import com.promptlib.proto.v1.ForkReply;
import com.promptlib.proto.v1.GalleryItem;
import com.promptlib.proto.v1.ListAdminPromptsReply;
import com.promptlib.proto.v1.ListCategoriesReply;
import com.promptlib.proto.v1.ListCollectionsReply;
import com.promptlib.proto.v1.ListFavoritesReply;
import com.promptlib.proto.v1.ListPromptsReply;
import com.promptlib.proto.v1.ListTagsReply;
import com.promptlib.proto.v1.ListVersionsReply;
import com.promptlib.proto.v1.PromptReply;
import com.promptlib.proto.v1.TagReply;
import com.promptlib.proto.v1.UserReply;
import com.promptlib.proto.v1.VersionReply;
import com.promptlib.proto.v1.VersionSummaryReply;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * docs/architecture/08-testing.md §4: every endpoint gets (1) no token, (2) wrong authorisation,
 * (3) correct authorisation. Admin routes additionally get a 403 case for a normal user.
 */
@QuarkusTest
class EdgeMatrixTest {

    private static final String ID = "0193a000-0000-7000-8000-000000000001";

    record Route(String name, String method, String path, String body, int expected) {
    }

    static final List<Route> USER_ROUTES = List.of(
            new Route("me", "GET", "/api/v1/me", null, 200),
            new Route("updateMe", "PATCH", "/api/v1/me", "{\"displayName\":\"A\"}", 200),
            new Route("listPrompts", "GET", "/api/v1/prompts", null, 200),
            new Route("createPrompt", "POST", "/api/v1/prompts", "{\"title\":\"t\"}", 201),
            new Route("getPrompt", "GET", "/api/v1/prompts/" + ID, null, 200),
            new Route("patchPrompt", "PATCH", "/api/v1/prompts/" + ID, "{\"title\":\"t2\"}", 200),
            new Route("deletePrompt", "DELETE", "/api/v1/prompts/" + ID, null, 204),
            new Route("favorite", "PUT", "/api/v1/prompts/" + ID + "/favorite", null, 200),
            new Route("unfavorite", "DELETE", "/api/v1/prompts/" + ID + "/favorite", null, 204),
            new Route("setTags", "PUT", "/api/v1/prompts/" + ID + "/tags", "{\"names\":[\"a\"]}", 200),
            new Route("listVersions", "GET", "/api/v1/prompts/" + ID + "/versions", null, 200),
            new Route("createVersion", "POST", "/api/v1/prompts/" + ID + "/versions",
                    "{\"messages\":[{\"role\":\"USER\",\"content\":\"x\"}]}", 201),
            new Route("getVersion", "GET", "/api/v1/prompts/" + ID + "/versions/1", null, 200),
            new Route("publish", "POST", "/api/v1/prompts/" + ID + "/versions/1/publish",
                    "{\"categoryId\":\"" + ID + "\"}", 200),
            new Route("unpublish", "POST", "/api/v1/prompts/" + ID + "/unpublish", null, 200),
            new Route("browse", "GET", "/api/v1/gallery", null, 200),
            new Route("fork", "POST", "/api/v1/gallery/" + ID + "/fork", null, 201),
            new Route("categories", "GET", "/api/v1/categories", null, 200),
            new Route("listCollections", "GET", "/api/v1/collections", null, 200),
            new Route("createCollection", "POST", "/api/v1/collections", "{\"name\":\"c\"}", 201),
            new Route("patchCollection", "PATCH", "/api/v1/collections/" + ID, "{\"name\":\"n\"}", 200),
            new Route("deleteCollection", "DELETE", "/api/v1/collections/" + ID, null, 204),
            new Route("addToCollection", "POST", "/api/v1/collections/" + ID + "/prompts",
                    "{\"promptId\":\"" + ID + "\"}", 204),
            new Route("listTags", "GET", "/api/v1/tags", null, 200),
            new Route("deleteTag", "DELETE", "/api/v1/tags/" + ID, null, 204),
            new Route("favorites", "GET", "/api/v1/favorites", null, 200),
            new Route("export", "GET", "/api/v1/export", null, 200),
            new Route("exportMd", "GET", "/api/v1/prompts/" + ID + "/export.md", null, 200)
    );

    static final List<Route> ADMIN_ROUTES = List.of(
            new Route("adminPrompts", "GET", "/api/v1/admin/prompts", null, 200),
            new Route("hide", "POST", "/api/v1/admin/prompts/" + ID + "/hide", "{\"reason\":\"why\"}", 200),
            new Route("unhide", "POST", "/api/v1/admin/prompts/" + ID + "/unhide", null, 200),
            new Route("adminCreateCategory", "POST", "/api/v1/admin/categories", "{\"name\":\"Data\"}", 201),
            new Route("adminPatchCategory", "PATCH", "/api/v1/admin/categories/" + ID, "{\"name\":\"D2\"}", 200),
            new Route("adminDeleteCategory", "DELETE", "/api/v1/admin/categories/" + ID, null, 200)
    );

    @InjectMock AccountClient accounts;
    @InjectMock PromptClient prompts;
    @InjectMock VersionClient versions;
    @InjectMock GalleryClient gallery;
    @InjectMock LibraryClient library;
    @InjectMock CategoryClient categories;
    @InjectMock AdminClient admin;
    @InjectMock ExportClient exports;

    @BeforeEach
    void defaults() throws Exception {
        UserReply me = UserReply.newBuilder().setId(ID).setUsername("alice").build();
        when(accounts.ensureUser(any(), any(), any(), any())).thenReturn(me);
        when(accounts.getMe()).thenReturn(me);
        when(accounts.updateProfile(any())).thenReturn(me);
        when(exports.library()).thenAnswer(i -> iterator("{\"schemaVersion\":\"1.0\""));
        when(exports.markdown(any())).thenAnswer(i -> iterator("# doc"));
    }

    private Iterator<ExportChunk> iterator(String... chunks) {
        List<ExportChunk> list = new ArrayList<>();
        for (String c : chunks) {
            list.add(ExportChunk.newBuilder().setData(c).build());
        }
        return list.iterator();
    }

    private Response call(Route r) {
        var request = given().contentType(ContentType.JSON);
        if (r.body() != null) {
            request = request.body(r.body());
        }
        return switch (r.method()) {
            case "GET" -> request.get(r.path());
            case "POST" -> request.post(r.path());
            case "PATCH" -> request.patch(r.path());
            case "PUT" -> request.put(r.path());
            case "DELETE" -> request.delete(r.path());
            default -> throw new IllegalArgumentException(r.method());
        };
    }

    @ParameterizedTest(name = "{0} without a token -> 401")
    @MethodSource("protectedRoutes")
    void noToken_returns401(Route route) {
        call(route).then().statusCode(401);
    }

    static List<Route> protectedRoutes() {
        List<Route> all = new ArrayList<>(USER_ROUTES);
        all.addAll(ADMIN_ROUTES);
        return all;
    }

    @ParameterizedTest(name = "{0} as a normal user -> 403")
    @MethodSource("adminRoutes")
    @TestSecurity(user = "bob", roles = "user")
    void nonAdmin_cannotReachAdminRoutes(Route route) {
        call(route).then().statusCode(403);
    }

    static List<Route> adminRoutes() {
        return ADMIN_ROUTES;
    }

    @ParameterizedTest(name = "{0} with a valid token -> {4}")
    @MethodSource("userRoutes")
    @TestSecurity(user = "alice", roles = "user")
    void authorizedUserRoute_succeeds(Route route) {
        stub(route.name());
        call(route).then().statusCode(route.expected());
    }

    static List<Route> userRoutes() {
        return USER_ROUTES;
    }

    @ParameterizedTest(name = "{0} as admin -> {4}")
    @MethodSource("adminAuthorizedRoutes")
    @TestSecurity(user = "root", roles = "admin")
    void authorizedAdminRoute_succeeds(Route route) {
        stub(route.name());
        call(route).then().statusCode(route.expected());
    }

    static List<Route> adminAuthorizedRoutes() {
        return ADMIN_ROUTES;
    }

    private void stub(String name) {
        PromptReply prompt = PromptReply.newBuilder().setId(ID).setTitle("t").build();
        switch (name) {
            case "listPrompts" -> when(prompts.list(any(), anyInt(), any()))
                    .thenReturn(ListPromptsReply.newBuilder().addItems(prompt).build());
            case "createPrompt" -> when(prompts.create(any(), any(), any(), any())).thenReturn(prompt);
            case "getPrompt" -> when(prompts.get(any())).thenReturn(prompt);
            case "patchPrompt" -> when(prompts.update(any(), any(), any())).thenReturn(prompt);
            case "setTags" -> when(library.setTags(any(), any()))
                    .thenReturn(ListTagsReply.newBuilder()
                            .addItems(TagReply.newBuilder().setId(ID).setName("a").build()).build());
            case "listVersions" -> when(versions.list(any()))
                    .thenReturn(ListVersionsReply.newBuilder()
                            .addItems(VersionSummaryReply.newBuilder().setVersionNumber(1).build()).build());
            case "createVersion" -> when(versions.create(any(), any(), any(), any()))
                    .thenReturn(VersionSummaryReply.newBuilder().setVersionNumber(2).build());
            case "getVersion" -> when(versions.get(any(), anyInt()))
                    .thenReturn(VersionReply.newBuilder().setVersionNumber(1).build());
            case "publish" -> when(versions.publish(any(), anyInt(), any())).thenReturn(prompt);
            case "unpublish" -> when(versions.unpublish(any())).thenReturn(prompt);
            case "browse" -> when(gallery.browse(any(), anyInt(), any(), any()))
                    .thenReturn(BrowseReply.newBuilder()
                            .addItems(GalleryItem.newBuilder().setId(ID).setTitle("t").build()).build());
            case "fork" -> when(gallery.fork(any()))
                    .thenReturn(ForkReply.newBuilder().setPromptId(ID).build());
            case "favorites" -> when(gallery.listFavorites())
                    .thenReturn(ListFavoritesReply.newBuilder()
                            .addItems(FavoriteItem.newBuilder().setId(ID).setTitle("t").build()).build());
            case "categories" -> when(categories.list())
                    .thenReturn(ListCategoriesReply.newBuilder()
                            .addItems(CategoryReply.newBuilder().setId(ID).setName("Coding").build()).build());
            case "listCollections" -> when(library.listCollections())
                    .thenReturn(ListCollectionsReply.newBuilder()
                            .addItems(CollectionReply.newBuilder().setId(ID).setName("c").build()).build());
            case "createCollection" -> when(library.createCollection(any(), any()))
                    .thenReturn(CollectionReply.newBuilder().setId(ID).setName("c").build());
            case "patchCollection" -> when(library.updateCollection(any(), any(), any(), any(), any()))
                    .thenReturn(CollectionReply.newBuilder().setId(ID).setName("c").build());
            case "listTags" -> when(library.listTags())
                    .thenReturn(ListTagsReply.newBuilder()
                            .addItems(TagReply.newBuilder().setId(ID).setName("a").build()).build());
            case "adminPrompts" -> when(admin.listPrompts())
                    .thenReturn(ListAdminPromptsReply.newBuilder()
                            .addItems(AdminPromptItem.newBuilder().setId(ID).setTitle("t").build()).build());
            case "adminCreateCategory" -> when(categories.create(any(), any(), any()))
                    .thenReturn(CategoryReply.newBuilder().setId(ID).setName("Data").build());
            case "adminPatchCategory" -> when(categories.update(any(), any(), any(), any(), any()))
                    .thenReturn(CategoryReply.newBuilder().setId(ID).setName("Data").build());
            case "adminDeleteCategory" -> when(categories.deactivate(any()))
                    .thenReturn(CategoryReply.newBuilder().setId(ID).setName("Data").build());
            default -> {
                // void operations need no stub
            }
        }
    }

    @Test
    @TestSecurity(user = "alice", roles = "user")
    void exportStreamsEveryChunkInOrder() {
        when(exports.library()).thenAnswer(i -> iterator("{\"a\":1", ",\"b\":2}"));
        given().when().get("/api/v1/export")
                .then().statusCode(200)
                .body(equalTo("{\"a\":1,\"b\":2}"));
    }

    @Test
    @TestSecurity(user = "alice", roles = "user")
    void markdownExportStreams() {
        when(exports.markdown(any())).thenAnswer(i -> iterator("# title\n", "body"));
        given().when().get("/api/v1/prompts/" + ID + "/export.md")
                .then().statusCode(200)
                .contentType(containsString("text/markdown"))
                .body(equalTo("# title\nbody"));
    }

    @Test
    @TestSecurity(user = "alice", roles = "user")
    void missingResourceIsAProblemJson404() {
        when(prompts.get(anyString()))
                .thenThrow(io.grpc.Status.NOT_FOUND.withDescription("NOT_FOUND").asRuntimeException());
        given().when().get("/api/v1/prompts/" + UUID.randomUUID())
                .then().statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }
}
