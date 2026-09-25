package com.openelements.spring.base.services.storage.memory;

import com.openelements.spring.base.services.storage.ObjectStoreException;
import com.openelements.spring.base.services.storage.ObjectNotFoundException;
import com.openelements.spring.base.services.storage.ObjectStore;
import com.openelements.spring.base.services.storage.StoredObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * In-memory {@link ObjectStore} for service-level tests (idempotency, deletion). This is a fake of
 * the interface, not a mock of the AWS SDK — the S3 implementation itself is tested against a real
 * Record Store. {@link #failDeletes} lets a test force a delete failure.
 *
 * <p>The backing map is concurrent and {@link #list} snapshots it, because the startup
 * {@code OrphanSweep} iterates the store on a background thread while tests write to it.
 */
public class InMemoryObjectStore implements ObjectStore {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    /**
     * When each object was written.
     *
     * <p>{@link #list} used to report {@code Instant.now()} as every object's last-modified time,
     * which made an object look freshly written no matter how long it had been there — and so made
     * {@code OrphanSweep}'s age filter impossible to exercise against this fake. Recording the write
     * time makes the fake able to express age, which is the only thing that filter reacts to.
     */
    private final Map<String, Instant> writtenAt = new ConcurrentHashMap<>();
    /** When set, every {@link #delete(String)} fails the way the S3 store reports a refused delete. */
    public volatile boolean failDeletes = false;
    /** When set, every {@link #put(String, InputStream, String)} fails after the source is drained. */
    public volatile boolean failPuts = false;
    /** The offset of the most recent ranged {@link #get(String, long, long)} call, for assertions. */
    public volatile long lastGetOffset = -1;
    /** The length of the most recent ranged {@link #get(String, long, long)} call, for assertions. */
    public volatile long lastGetLength = -1;

    /** Creates an empty store. */
    public InMemoryObjectStore() {
    }

    @Override
    public void put(String key, InputStream data, String contentType) {
        try {
            // Drain the stream first so a size-limited source still throws its own overflow.
            byte[] bytes = data.readAllBytes();
            if (failPuts) {
                // Mirror the real S3 store, which reports a failed part as ObjectStoreException.
                throw new ObjectStoreException("put rejected by test", null);
            }
            objects.put(key, bytes);
            writtenAt.put(key, Instant.now());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public InputStream get(String key) {
        byte[] bytes = objects.get(key);
        if (bytes == null) {
            throw new ObjectNotFoundException(key);
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public InputStream get(String key, long offset, long length) {
        lastGetOffset = offset;
        lastGetLength = length;
        byte[] bytes = objects.get(key);
        if (bytes == null) {
            throw new ObjectNotFoundException(key);
        }
        int from = (int) Math.min(offset, bytes.length);
        int to = (int) Math.min(offset + length, bytes.length);
        byte[] slice = new byte[to - from];
        System.arraycopy(bytes, from, slice, 0, to - from);
        return new ByteArrayInputStream(slice);
    }

    @Override
    public OptionalLong size(String key) {
        byte[] bytes = objects.get(key);
        return bytes == null ? OptionalLong.empty() : OptionalLong.of(bytes.length);
    }

    @Override
    public boolean delete(String key) {
        if (failDeletes) {
            // Mirror the real S3 store, which surfaces delete failures as ObjectStoreException.
            throw new ObjectStoreException("delete rejected by test", null);
        }
        writtenAt.remove(key);
        return objects.remove(key) != null;
    }

    @Override
    public Stream<StoredObject> list(String prefix) {
        // Snapshot so a concurrent write (e.g. a test upload) cannot disturb the iteration.
        List<StoredObject> snapshot = new ArrayList<>();
        objects.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                snapshot.add(new StoredObject(key, value.length,
                        writtenAt.getOrDefault(key, Instant.now())));
            }
        });
        return snapshot.stream();
    }

    @Override
    public int abortIncompleteUploadsOlderThan(java.time.Instant threshold) {
        // No multipart uploads in the in-memory fake; nothing to reclaim.
        return 0;
    }

    /**
     * Whether an object is stored under a key.
     *
     * @param key the object key
     * @return {@code true} if the key has an object
     */
    public boolean contains(String key) {
        return objects.containsKey(key);
    }

    /**
     * The stored bytes for a key.
     *
     * @param key the object key
     * @return the stored bytes, or {@code null} if the key has no object
     */
    public byte @Nullable [] contentOf(String key) {
        return objects.get(key);
    }
}
