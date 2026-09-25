package com.openelements.spring.base.services.storage.s3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.stream.Stream;

import com.openelements.spring.base.services.storage.ObjectNotFoundException;
import com.openelements.spring.base.services.storage.ObjectStore;
import com.openelements.spring.base.services.storage.ObjectStoreException;
import com.openelements.spring.base.services.storage.StoredObject;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

/**
 * {@link ObjectStore} on the AWS SDK v2 synchronous client.
 *
 * <p>Uploads stream in bounded {@value #PART_SIZE_BYTES}-byte parts: a small payload becomes a single
 * {@code PutObject} once its (short) length is known after reading, a large one becomes a multipart
 * upload whose parts are uploaded and discarded one at a time, so heap use stays bounded no matter
 * how long the audio is.
 */
public class S3ObjectStore implements ObjectStore {

    /** Part size for multipart uploads (8 MiB, above S3's 5 MiB minimum for non-final parts). */
    static final int PART_SIZE_BYTES = 8 * 1024 * 1024;

    private final S3Client s3;
    private final String bucket;

    /**
     * Creates a store over one bucket.
     *
     * @param s3     the client to issue requests through
     * @param bucket the bucket every key lives in
     */
    public S3ObjectStore(final S3Client s3, final String bucket) {
        this.s3 = Objects.requireNonNull(s3, "s3 must not be null");
        this.bucket = Objects.requireNonNull(bucket, "bucket must not be null");
    }

    @Override
    public void put(final String key, final InputStream data, final String contentType) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        try (data) {
            final byte[] firstPart = new byte[PART_SIZE_BYTES];
            final int firstLen = readFully(data, firstPart);

            if (firstLen < PART_SIZE_BYTES) {
                // The whole stream (including an empty one) fits in one buffer: a single PutObject
                // with the now-known length. No multipart machinery for small objects.
                s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType)
                                .contentLength((long) firstLen),
                        RequestBody.fromInputStream(new ByteArrayInputStream(firstPart, 0, firstLen),
                                firstLen));
                return;
            }
            putMultipart(key, contentType, data, firstPart);
        } catch (final IOException e) {
            throw new ObjectStoreException("Could not read the stream for key " + key, e);
        }
    }

    private void putMultipart(final String key, final String contentType, final InputStream data, final byte[] firstPart) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(firstPart, "firstPart must not be null");
        final CreateMultipartUploadResponse created = s3.createMultipartUpload(
                b -> b.bucket(bucket).key(key).contentType(contentType));
        final String uploadId = created.uploadId();
        final List<CompletedPart> parts = new ArrayList<>();
        try {
            int partNumber = 1;
            byte[] buffer = firstPart;
            int len = PART_SIZE_BYTES;
            while (len == PART_SIZE_BYTES) {
                parts.add(uploadPart(key, uploadId, partNumber, buffer, len));
                partNumber++;
                buffer = new byte[PART_SIZE_BYTES];
                len = readFully(data, buffer);
                if (len == 0) {
                    break;
                }
                if (len < PART_SIZE_BYTES) {
                    parts.add(uploadPart(key, uploadId, partNumber, buffer, len));
                    break;
                }
            }
            final List<CompletedPart> finalParts = parts;
            s3.completeMultipartUpload(b -> b.bucket(bucket).key(key).uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(finalParts).build()));
        } catch (final RuntimeException | IOException e) {
            // Includes a failure reading the source stream mid-upload: abort so no incomplete
            // multipart upload is left accruing storage (the sweep lists objects, not uploads).
            s3.abortMultipartUpload(b -> b.bucket(bucket).key(key).uploadId(uploadId));
            throw new ObjectStoreException("Multipart upload failed for key " + key, e);
        }
    }

    private CompletedPart uploadPart(final String key, final String uploadId, final int partNumber, final byte[] buffer,
            final int length) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(uploadId, "uploadId must not be null");
        Objects.requireNonNull(buffer, "buffer must not be null");
        Objects.requireNonNull(buffer, "buffer must not be null");
        final UploadPartResponse response = s3.uploadPart(
                b -> b.bucket(bucket).key(key).uploadId(uploadId).partNumber(partNumber),
                RequestBody.fromInputStream(new ByteArrayInputStream(buffer, 0, length), length));
        return CompletedPart.builder().partNumber(partNumber).eTag(response.eTag()).build();
    }

    @Override
    public InputStream get(final String key) {
        Objects.requireNonNull(key, "key must not be null");
        try {
            return s3.getObject(b -> b.bucket(bucket).key(key));
        } catch (final NoSuchKeyException e) {
            throw new ObjectNotFoundException(key, e);
        }
    }

    @Override
    public InputStream get(final String key, final long offset, final long length) {
        Objects.requireNonNull(key, "key must not be null");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if(length < 0) {
            throw new IllegalArgumentException("length must not be negative");
        }
        if (length == 0) {
            return new ByteArrayInputStream(new byte[0]);
        }
        long last = offset + length - 1;
        try {
            return s3.getObject(b -> b.bucket(bucket).key(key).range("bytes=" + offset + "-" + last));
        } catch (final NoSuchKeyException e) {
            throw new ObjectNotFoundException(key, e);
        }
    }

    @Override
    public OptionalLong size(final String key) {
        try {
            return OptionalLong.of(s3.headObject(b -> b.bucket(bucket).key(key)).contentLength());
        } catch (final NoSuchKeyException e) {
            return OptionalLong.empty();
        }
    }

    @Override
    public boolean delete(final String key) {
        Objects.requireNonNull(key, "key must not be null");
        final boolean existed = size(key).isPresent();
        s3.deleteObject(b -> b.bucket(bucket).key(key));
        return existed;
    }

    @Override
    public Stream<StoredObject> list(final String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        return s3.listObjectsV2Paginator(b -> b.bucket(bucket).prefix(prefix)).contents().stream()
                .map(o -> new StoredObject(o.key(), o.size(), o.lastModified()));
    }

    @Override
    public int abortIncompleteUploadsOlderThan(final Instant threshold) {
        Objects.requireNonNull(threshold, "threshold must not be null");
        int aborted = 0;
        for (var upload : s3.listMultipartUploadsPaginator(b -> b.bucket(bucket)).uploads()) {
            if (upload.initiated().isBefore(threshold)) {
                s3.abortMultipartUpload(b -> b.bucket(bucket).key(upload.key())
                        .uploadId(upload.uploadId()));
                aborted++;
            }
        }
        return aborted;
    }

    /** Reads until the buffer is full or the stream ends; returns the number of bytes read. */
    private static int readFully(final InputStream in, final byte[] buffer) throws IOException {
        Objects.requireNonNull(in, "in must not be null");
        Objects.requireNonNull(buffer, "buffer must not be null");
        int total = 0;
        while (total < buffer.length) {
            int read = in.read(buffer, total, buffer.length - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }
}
