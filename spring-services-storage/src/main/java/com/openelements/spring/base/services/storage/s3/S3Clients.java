package com.openelements.spring.base.services.storage.s3;

import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

/** Client settings an {@link S3ObjectStore} needs its {@code S3Client} to have been built with. */
public final class S3Clients {

    private S3Clients() {
    }

    /**
     * Makes the Java SDK send each request body in one piece instead of {@code aws-chunked}.
     *
     * <p>By default the SDK frames a body as {@code aws-chunked} with a trailing CRC32
     * ({@code x-amz-trailer: x-amz-checksum-crc32}). Not every S3-compatible provider implements
     * trailing checksums, and one that does not answers {@code 501 NotImplemented} — so chunked
     * encoding is switched off and the body goes out whole, with its checksum in an ordinary header
     * the store verifies.
     *
     * <p>This costs one extra pass over each part and no memory: {@link S3ObjectStore} already hands
     * the SDK a {@link java.io.ByteArrayInputStream} over a bounded buffer, which is both resettable
     * and already resident, so nothing is buffered that was not already there.
     *
     * <p>Nothing else breaks from it: {@code aws-chunked} is an optimisation, so AWS and other
     * S3-compatible providers accept a body sent in one piece just as well.
     *
     * <p>Public because a test that builds its own client must be able to build it exactly the way
     * the configured bean is built. A test passing against a more lenient client would prove nothing
     * about the one the application runs with.
     *
     * @param builder the builder to configure
     * @return the same builder, for chaining
     */
    public static S3ClientBuilder withoutChunkedEncoding(final S3ClientBuilder builder) {
        return builder.serviceConfiguration(
                S3Configuration.builder().chunkedEncodingEnabled(false).build());
    }
}
