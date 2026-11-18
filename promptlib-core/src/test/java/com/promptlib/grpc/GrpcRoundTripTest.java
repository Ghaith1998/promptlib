package com.promptlib.grpc;

import com.promptlib.domain.account.model.AppUser;
import com.promptlib.domain.account.service.AccountService;
import com.promptlib.grpc.interceptor.CallerContextInterceptor;
import com.promptlib.proto.v1.CreatePromptRequest;
import com.promptlib.proto.v1.GetPromptRequest;
import com.promptlib.proto.v1.MessageInput;
import com.promptlib.proto.v1.PromptReply;
import com.promptlib.proto.v1.PromptServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A real wire round-trip: test-owned gRPC server, the production interceptor, generated stubs.
 * This avoids Quarkus' randomised test port while still exercising serialization, metadata and
 * status mapping end to end.
 */
@QuarkusTest
class GrpcRoundTripTest {

    @Inject
    com.promptlib.domain.prompt.service.PromptService promptsService;
    @Inject
    AccountService accounts;

    private Server server;
    private ManagedChannel channel;
    private PromptServiceGrpc.PromptServiceBlockingStub stub;

    @BeforeEach
    void start() throws IOException {
        PromptGrpcService service = new PromptGrpcService();
        service.prompts = promptsService;
        server = ServerBuilder.forPort(0)
                .addService(service)
                .intercept(new CallerContextInterceptor())
                .build()
                .start();
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort()).usePlaintext().build();
        stub = PromptServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void stop() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private Metadata metadata(AppUser user, String roles) {
        Metadata md = new Metadata();
        md.put(Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER), user.id.toString());
        md.put(Metadata.Key.of("x-user-sub", Metadata.ASCII_STRING_MARSHALLER), user.keycloakSub);
        md.put(Metadata.Key.of("x-user-roles", Metadata.ASCII_STRING_MARSHALLER), roles);
        return md;
    }

    @Test
    void createsAndFetchesOverTheWire() {
        AppUser user = accounts.ensureUser(UUID.randomUUID().toString(), "wire-" + UUID.randomUUID(), "w@example.com");

        PromptReply created = stub.withInterceptors(
                        MetadataUtils.newAttachHeadersInterceptor(metadata(user, "user")))
                .create(CreatePromptRequest.newBuilder()
                        .setTitle("wire prompt")
                        .addMessages(MessageInput.newBuilder().setRole("USER").setContent("hello").build())
                        .build());

        PromptReply fetched = stub.withInterceptors(
                        MetadataUtils.newAttachHeadersInterceptor(metadata(user, "user")))
                .get(GetPromptRequest.newBuilder().setPromptId(created.getId()).build());

        assertEquals("wire prompt", fetched.getTitle());
        assertEquals("PRIVATE", fetched.getVisibility());
    }

    @Test
    void missingIdentityIsUnauthenticated() {
        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> stub.get(GetPromptRequest.newBuilder()
                        .setPromptId(UUID.randomUUID().toString()).build()));
        assertEquals(Status.Code.UNAUTHENTICATED, ex.getStatus().getCode());
    }

    @Test
    void domainFailureCarriesTheProblemCodeInTheStatus() {
        AppUser user = accounts.ensureUser(UUID.randomUUID().toString(), "wire2-" + UUID.randomUUID(), "w2@example.com");

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata(user, "user")))
                        .get(GetPromptRequest.newBuilder().setPromptId(UUID.randomUUID().toString()).build()));

        assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        assertEquals("NOT_FOUND", ex.getStatus().getDescription());
    }
}
