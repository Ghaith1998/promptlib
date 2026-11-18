package com.promptlib.edge.api;

import com.promptlib.edge.client.AccountClient;
import com.promptlib.edge.client.AdminClient;
import com.promptlib.edge.client.PromptClient;
import com.promptlib.proto.v1.PromptReply;
import com.promptlib.proto.v1.UserReply;
import io.grpc.Status;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import io.restassured.http.ContentType;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class EdgeApiTest {

    @InjectMock
    AccountClient accounts;
    @InjectMock
    PromptClient prompts;
    @InjectMock
    AdminClient admin;

    private UserReply mirrored;

    @BeforeEach
    void mirrorUser() {
        mirrored = UserReply.newBuilder()
                .setId(UUID.randomUUID().toString()).setUsername("alice").build();
        when(accounts.ensureUser(anyString(), anyString(), anyString(), anySet())).thenReturn(mirrored);
        when(accounts.getMe()).thenReturn(mirrored);
    }

    @Test
    void anonymousRequestsAreRejected() {
        given().when().get("/api/v1/me").then().statusCode(401);
    }

    @Test
    void registrationIsPublic() {
        when(accounts.register(any(), any(), any(), any()))
                .thenReturn(UserReply.newBuilder().setId("kc-1").setUsername("bob")
                        .setEmail("bob@example.com").build());

        given().contentType(ContentType.JSON)
                .body("{\"username\":\"bob\",\"email\":\"bob@example.com\",\"password\":\"longenough\"}")
                .when().post("/api/v1/auth/register")
                .then().statusCode(201)
                .body("username", equalTo("bob"));
    }

    @Test
    @TestSecurity(user = "alice", roles = "user")
    void meReturnsTheMirroredProfile() {
        given().when().get("/api/v1/me")
                .then().statusCode(200)
                .body("username", equalTo("alice"));
    }

    @Test
    @TestSecurity(user = "alice", roles = "user")
    void notFoundMapsTo404ProblemJson() {
        when(prompts.get(anyString()))
                .thenThrow(Status.NOT_FOUND.withDescription("NOT_FOUND").asRuntimeException());

        given().when().get("/api/v1/prompts/" + UUID.randomUUID())
                .then().statusCode(404)
                .contentType(containsString("application/problem+json"))
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "alice", roles = "user")
    void validationMapsTo422WithTheDomainCode() {
        when(prompts.get(anyString()))
                .thenThrow(Status.INVALID_ARGUMENT.withDescription("VARIABLE_MISMATCH").asRuntimeException());

        given().when().get("/api/v1/prompts/" + UUID.randomUUID())
                .then().statusCode(422)
                .body("code", equalTo("VARIABLE_MISMATCH"));
    }

    @Test
    @TestSecurity(user = "alice", roles = "user")
    void adminEndpointsRejectNonAdmins() {
        given().when().get("/api/v1/admin/prompts").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "root", roles = "admin")
    void adminEndpointsWorkForAdmins() {
        when(admin.listPrompts())
                .thenReturn(com.promptlib.proto.v1.ListAdminPromptsReply.newBuilder().build());

        given().when().get("/api/v1/admin/prompts").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "alice", roles = "user")
    void promptListIsMapped() {
        when(prompts.list(any(), any(Integer.class), any()))
                .thenReturn(com.promptlib.proto.v1.ListPromptsReply.newBuilder()
                        .addItems(PromptReply.newBuilder().setId("p1").setTitle("t").build())
                        .build());

        given().when().get("/api/v1/prompts")
                .then().statusCode(200)
                .body("items[0].title", equalTo("t"));
    }
}
