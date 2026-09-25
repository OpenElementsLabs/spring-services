package com.openelements.spring.base.services.storage;


import java.io.InputStream;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.stream.Stream;

/**
 * Abstraction over an S3-compatible object store. The single implementation targets AWS S3, Hetzner
 * Object Storage and Record Store through an explicit endpoint and path-style addressing.
 *
 * <p>Writes stream without buffering the whole payload; reads return the store's response stream
 * directly. Audio of any length must move through here without being materialised in the backend's
 * heap or on its disk.
 */
public interface ObjectStore {

    /**
     * Writes a stream under a key. The length need not be known in advance; the data is uploaded in
     * bounded parts so an hour of audio never lands in memory.
     *
     * @param key         the object key
     * @param data        the payload; fully consumed and closed by the store
     * @param contentType the object's content type
     */
    void put(String key, InputStream data, String contentType);

    /**
     * Opens the object for reading.
     *
     * @param key the object key
     * @return the object's content stream (caller closes it)
     * @throws ObjectNotFoundException if no object exists under the key
     */
    InputStream get(String key);

    /**
     * Opens a byte range of the object. A range extending past the end returns the available bytes
     * rather than failing.
     *
     * @param key    the object key
     * @param offset the first byte to return
     * @param length the maximum number of bytes to return
     * @return the requested slice (caller closes it)
     * @throws ObjectNotFoundException if no object exists under the key
     */
    InputStream get(String key, long offset, long length);

    /**
     * The size of the object, or empty if it does not exist — distinguishable from an object of
     * length zero.
     *
     * @param key the object key
     * @return the size in bytes, or empty if there is no such object
     */
    OptionalLong size(String key);

    /**
     * Deletes the object. Idempotent: deleting a missing key succeeds.
     *
     * @param key the object key
     * @return {@code true} if an object was removed, {@code false} if there was nothing to remove
     */
    boolean delete(String key);

    /**
     * Lists objects under a prefix.
     *
     * @param prefix the key prefix
     * @return the matching objects
     */
    Stream<StoredObject> list(String prefix);

    /**
     * Aborts incomplete multipart uploads initiated before the given moment. Such uploads are
     * invisible to {@link #list(String)} — only reachable through the multipart-uploads listing — so
     * the orphan sweep must reclaim them separately or they accumulate unreported.
     *
     * @param threshold abort uploads initiated before this instant
     * @return the number of uploads aborted
     */
    int abortIncompleteUploadsOlderThan(Instant threshold);
}
