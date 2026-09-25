package com.openelements.spring.base.services.storage.s3;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.services.storage.ObjectStore;
import com.openelements.spring.base.services.storage.ObjectStoreContractTest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * The {@link ObjectStore} contract against a real S3 server, plus the multipart boundary.
 *
 * <p>A stub would not do here. The behaviour that made this suite necessary — what an S3 server
 * answers to a range whose first byte lies at or past the object's end — is exactly the thing a stub
 * would have to assume, and the assumption is what was wrong. The server answers {@code 416}, and
 * {@link S3ObjectStore} has to turn that into the empty result the interface promises.
 *
 * <p>The client is built through {@link S3Clients#withoutChunkedEncoding}, the same helper the
 * auto-configuration uses, so this never passes against a client configured more leniently than the
 * one an application gets.
 */
@Testcontainers
@DisplayName("S3ObjectStore")
class S3ObjectStoreTest extends ObjectStoreContractTest {

    private static final int S3MOCK_PORT = 9090;

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> S3 =
            new GenericContainer<>(DockerImageName.parse("adobe/s3mock:5.2.3"))
                    .withExposedPorts(S3MOCK_PORT)
                    .waitingFor(Wait.forHttp("/").forStatusCode(200));

    private S3Client client;

    private String bucket;

    private S3ObjectStore store;

    @BeforeEach
    void setUp() {
        client = S3Clients.withoutChunkedEncoding(S3Client.builder()
                        .endpointOverride(URI.create(
                                "http://" + S3.getHost() + ":" + S3.getMappedPort(S3MOCK_PORT)))
                        .region(Region.US_EAST_1)
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("test", "test"))))
                .forcePathStyle(true)
                .build();
        // A bucket per test, so list(prefix) and delete see only what their own test wrote.
        bucket = "contract-" + UUID.randomUUID();
        client.createBucket(b -> b.bucket(bucket));
        store = new S3ObjectStore(client, bucket);
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    @Override
    protected ObjectStore store() {
        return store;
    }

    @Test
    @DisplayName("A payload larger than one part uploads as multipart and reads back whole")
    void multipartUpload() throws IOException {
        final byte[] payload = payloadOf(S3ObjectStore.PART_SIZE_BYTES + 1024);

        store.put("large.bin", new ByteArrayInputStream(payload), "application/octet-stream");

        assertThat(store.size("large.bin")).hasValue((long) payload.length);
        try (var stream = store.get("large.bin")) {
            assertThat(stream.readAllBytes()).isEqualTo(payload);
        }
    }

    @Test
    @DisplayName("A payload of exactly one part still takes the multipart path and reads back whole")
    void payloadOfExactlyOnePart() throws IOException {
        final byte[] payload = payloadOf(S3ObjectStore.PART_SIZE_BYTES);

        store.put("exact.bin", new ByteArrayInputStream(payload), "application/octet-stream");

        assertThat(store.size("exact.bin")).hasValue((long) payload.length);
        try (var stream = store.get("exact.bin")) {
            assertThat(stream.readAllBytes()).isEqualTo(payload);
        }
    }

    @Test
    @DisplayName("A ranged read reaches into a multipart object without reading it whole")
    void rangedReadOfAMultipartObject() throws IOException {
        final byte[] payload = payloadOf(S3ObjectStore.PART_SIZE_BYTES + 1024);

        store.put("large.bin", new ByteArrayInputStream(payload), "application/octet-stream");

        try (var stream = store.get("large.bin", S3ObjectStore.PART_SIZE_BYTES, 4)) {
            assertThat(stream.readAllBytes()).isEqualTo(new byte[]{
                    payload[S3ObjectStore.PART_SIZE_BYTES],
                    payload[S3ObjectStore.PART_SIZE_BYTES + 1],
                    payload[S3ObjectStore.PART_SIZE_BYTES + 2],
                    payload[S3ObjectStore.PART_SIZE_BYTES + 3]});
        }
    }

    @Test
    @DisplayName("There is nothing to reclaim while no upload has been interrupted")
    void nothingToAbort() {
        put("settled.txt", "content");

        assertThat(store.abortIncompleteUploadsOlderThan(Instant.now())).isZero();
    }

    @Test
    @DisplayName("The content type survives the round trip, unlike in the file store")
    void contentTypeIsStored() {
        store.put("typed.png", new ByteArrayInputStream("x".getBytes(UTF_8)), "image/png");

        assertThat(client.headObject(b -> b.bucket(bucket).key("typed.png")).contentType())
                .isEqualTo("image/png");
    }

    /** Bytes with a recognisable pattern, so a misplaced part shows up as a mismatch. */
    private static byte[] payloadOf(final int size) {
        final byte[] payload = new byte[size];
        for (int i = 0; i < size; i++) {
            payload[i] = (byte) (i % 251);
        }
        return payload;
    }
}
