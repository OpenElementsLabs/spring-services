package com.openelements.spring.base.services.storage.s3;

import java.net.URI;

import com.openelements.spring.base.services.storage.ObjectStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Wires the AWS SDK v2 {@link S3Client} and the {@link ObjectStore}.
 *
 * <p>The bucket and credentials are read through {@code @Value}, which fails start-up when the
 * underlying {@code S3_BUCKET} / {@code S3_ACCESS_KEY} / {@code S3_SECRET_KEY} placeholder cannot be
 * resolved (unlike {@code @ConfigurationProperties}, which would silently keep the unresolved
 * string). A backend that starts without a store would accept uploads it cannot keep.
 */
@Configuration
public class S3Config {

    /** Creates the configuration; Spring instantiates it. */
    public S3Config() {
    }

    /**
     * The SDK client every request goes through, closed when the context shuts down.
     *
     * @param endpoint  the store's endpoint
     * @param region    the region to sign requests for
     * @param accessKey the access key
     * @param secretKey the secret key
     * @return the client
     */
    @Bean(destroyMethod = "close")
    public S3Client s3Client(
            @Value("${storage.s3.endpoint}") String endpoint,
            @Value("${storage.s3.region}") String region,
            @Value("${storage.s3.access-key}") String accessKey,
            @Value("${storage.s3.secret-key}") String secretKey) {
        return withoutChunkedEncoding(S3Client.builder()
                        .endpointOverride(URI.create(endpoint))
                        .region(Region.of(region))
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey))))
                // Several S3-compatible providers do not support virtual-host addressing.
                .forcePathStyle(true)
                .build();
    }

    /**
     * Makes the Java SDK send each request body in one piece instead of {@code aws-chunked}.
     *
     * <p>By default the SDK frames a body as {@code aws-chunked} with a trailing CRC32
     * ({@code x-amz-trailer: x-amz-checksum-crc32}). Record Store does not implement trailing
     * checksums and answers {@code 501 NotImplemented} (since 0.1.2; earlier versions returned a
     * generic {@code 400 InvalidRequest}), so chunked encoding is switched off and the body goes
     * out whole, with its checksum in an ordinary header the store verifies.
     *
     * <p>This costs one extra pass over each part and no memory: {@code S3ObjectStore} already
     * hands the SDK a {@link java.io.ByteArrayInputStream} over a bounded {@code PART_SIZE_BYTES}
     * buffer, which is both resettable and already resident, so nothing is buffered that was not
     * already there.
     *
     * <p>The setting is not specific to Record Store in the sense of breaking anything else:
     * {@code aws-chunked} is an optimisation, so AWS and other S3-compatible providers accept a
     * body sent in one piece just as well.
     *
     * <p>A second departure from the SDK's defaults used to live here: payload signing, because the
     * SDK's {@code x-amz-content-sha256: UNSIGNED-PAYLOAD} was refused. Record Store 0.1.2 accepts
     * it (record-store#74), so that one is gone and the SDK's default signing applies.
     *
     * @param builder the builder to configure
     * @return the same builder, for chaining
     */
    // Public, not package-private: the sweep's integration test lives in the orphan package and
    // builds its client through this very helper on purpose, so a test can never pass against a
    // client configured more leniently than the one the application bean gets.
    public static S3ClientBuilder withoutChunkedEncoding(S3ClientBuilder builder) {
        return builder.serviceConfiguration(
                S3Configuration.builder().chunkedEncodingEnabled(false).build());
    }

    /**
     * The store the application talks to.
     *
     * @param s3Client the configured client
     * @param bucket   the bucket every key lives in
     * @return the store
     */
    @Bean
    public ObjectStore objectStore(S3Client s3Client, @Value("${storage.s3.bucket}") String bucket) {
        return new S3ObjectStore(s3Client, bucket);
    }
}
