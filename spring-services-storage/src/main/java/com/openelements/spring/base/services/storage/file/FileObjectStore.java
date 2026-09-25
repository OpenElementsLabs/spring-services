package com.openelements.spring.base.services.storage.file;

import com.openelements.spring.base.services.storage.ObjectNotFoundException;
import com.openelements.spring.base.services.storage.ObjectStore;
import com.openelements.spring.base.services.storage.ObjectStoreException;
import com.openelements.spring.base.services.storage.StoredObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * An {@link ObjectStore} backed by a directory on the local file system.
 *
 * <p>It exists for deployments that have no object store to point at — a single machine with a disk,
 * or a developer who does not want a container running. It keeps the interface's promises rather than
 * approximating them, and the three that take work are these:
 *
 * <ul>
 *   <li><strong>A key never becomes visible half-written.</strong> A {@link #put} streams into a
 *       scratch file under {@code uploads/} and then <em>moves</em> it into place, so a reader sees
 *       either the previous object or the complete new one. This is what makes a failed upload
 *       equivalent to S3's aborted multipart upload rather than a truncated object that later passes
 *       for audio.
 *   <li><strong>Nothing is materialised.</strong> Writes copy stream-to-file, reads hand back a file
 *       stream, and a ranged read positions a {@link SeekableByteChannel} instead of skipping bytes —
 *       so seeking into an hour of audio costs one seek, not an hour of reads.
 *   <li><strong>A key cannot escape the root.</strong> Keys are paths here, unlike in S3 where they
 *       are opaque strings, so {@code ../} would be a directory traversal. Every segment is validated
 *       and the resolved path is checked against the root.
 * </ul>
 *
 * <p>The scratch directory also gives {@link #abortIncompleteUploadsOlderThan} something real to do:
 * a crash mid-{@code put} leaves a file there, invisible to {@link #list} exactly as an incomplete
 * multipart upload is invisible to S3's listing, and reclaimed by the same sweep.
 *
 * <p>The content type passed to {@link #put} is <strong>ignored</strong>. A file system has nowhere to
 * keep it, and the interface offers no way to read it back — the application stores an object's
 * content type in its own database. Inventing a sidecar file for a value nobody reads from here would
 * be a second source of truth for it.
 *
 * <p>This class registers no bean of its own: an application chooses one {@code ObjectStore}
 * implementation, and making two of them self-registering would decide that by classpath order.
 */
public class FileObjectStore implements ObjectStore {

    /** Objects live here, one file per key, directories mirroring the key's slashes. */
    private static final String OBJECTS_DIR = "objects";

    /** In-flight writes live here until they are moved into {@link #OBJECTS_DIR}. */
    private static final String UPLOADS_DIR = "uploads";

    private final Path objects;
    private final Path uploads;

    /**
     * Opens (and creates) a store rooted at {@code root}.
     *
     * @param root the directory the store owns; created if missing
     * @throws ObjectStoreException if the directories cannot be created
     */
    public FileObjectStore(final Path root) {
        Objects.requireNonNull(root, "root must not be null");
        final Path base = root.toAbsolutePath().normalize();
        this.objects = base.resolve(OBJECTS_DIR);
        this.uploads = base.resolve(UPLOADS_DIR);
        try {
            Files.createDirectories(objects);
            Files.createDirectories(uploads);
        } catch (final IOException e) {
            throw new ObjectStoreException("Could not create the object store under " + root, e);
        }
    }

    @Override
    public void put(final String key, final InputStream data, final String contentType) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        final Path target = pathOf(key);
        final Path scratch = uploads.resolve(UUID.randomUUID() + ".part");
        try (data) {
            // Stream to disk first. A failure here — including a source that throws mid-stream — must
            // leave the key untouched, which is why nothing is written at the target path yet.
            Files.copy(data, scratch, StandardCopyOption.REPLACE_EXISTING);
            Files.createDirectories(target.getParent());
            moveIntoPlace(scratch, target);
        } catch (final IOException e) {
            deleteQuietly(scratch);
            throw new ObjectStoreException("Could not write the object for key " + key, e);
        } catch (final RuntimeException e) {
            // A source stream that throws its own failure (a size limit, say) keeps that exception:
            // the caller distinguishes those, and wrapping would flatten them into one store error.
            deleteQuietly(scratch);
            throw e;
        }
    }

    @Override
    public InputStream get(final String key) {
        Objects.requireNonNull(key, "key must not be null");
        try {
            return Files.newInputStream(pathOf(key), StandardOpenOption.READ);
        } catch (final NoSuchFileException e) {
            throw new ObjectNotFoundException(key, e);
        } catch (final IOException e) {
            throw new ObjectStoreException("Could not read the object for key " + key, e);
        }
    }

    @Override
    public InputStream get(final String key, final long offset, final long length) {
        Objects.requireNonNull(key, "key must not be null");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative");
        }
        final Path path = pathOf(key);
        if (length == 0) {
            // Empty, but only for an object that exists: a zero-length read of a missing key is a
            // wrong key, not "no bytes".
            if (!Files.isRegularFile(path)) {
                throw new ObjectNotFoundException(key);
            }
            return InputStream.nullInputStream();
        }
        SeekableByteChannel channel = null;
        try {
            channel = Files.newByteChannel(path, StandardOpenOption.READ);
            if (offset >= channel.size()) {
                // A range starting past the end yields the available bytes — none — as the interface
                // specifies, rather than failing the way an out-of-range seek would.
                channel.close();
                return InputStream.nullInputStream();
            }
            channel.position(offset);
            return new BoundedInputStream(Channels.newInputStream(channel), length);
        } catch (final NoSuchFileException e) {
            throw new ObjectNotFoundException(key, e);
        } catch (final IOException e) {
            closeQuietly(channel);
            throw new ObjectStoreException("Could not read the object for key " + key, e);
        }
    }

    @Override
    public OptionalLong size(final String key) {
        Objects.requireNonNull(key, "key must not be null");
        try {
            return OptionalLong.of(Files.size(pathOf(key)));
        } catch (final NoSuchFileException e) {
            return OptionalLong.empty();
        } catch (final IOException e) {
            throw new ObjectStoreException("Could not stat the object for key " + key, e);
        }
    }

    @Override
    public boolean delete(final String key) {
        Objects.requireNonNull(key, "key must not be null");
        final Path path = pathOf(key);
        try {
            final boolean removed = Files.deleteIfExists(path);
            // The directories a key's slashes created are an artefact of this implementation — S3 has
            // none — so an emptied one is swept away rather than left to accumulate per deleted object.
            pruneEmptyParents(path.getParent());
            return removed;
        } catch (final IOException e) {
            throw new ObjectStoreException("Could not delete the object for key " + key, e);
        }
    }

    @Override
    public Stream<StoredObject> list(final String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        final List<StoredObject> snapshot = new ArrayList<>();
        try {
            Files.walkFileTree(objects, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                    final String key = keyOf(file);
                    if (key.startsWith(prefix)) {
                        snapshot.add(new StoredObject(key, attrs.size(),
                                attrs.lastModifiedTime().toInstant()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(final Path file, final IOException e) {
                    // A file that vanished between the directory read and the stat was deleted by a
                    // concurrent caller. The orphan sweep walks this store while uploads run, and a
                    // listing that dies on that race would be worse than one that omits the file.
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (final IOException e) {
            throw new ObjectStoreException("Could not list objects under " + prefix, e);
        }
        // A snapshot rather than a lazy walk, for the same reason: the caller iterates while other
        // threads write, and the walk's own cursor would be the thing that breaks.
        return snapshot.stream();
    }

    @Override
    public int abortIncompleteUploadsOlderThan(final Instant threshold) {
        Objects.requireNonNull(threshold, "threshold must not be null");
        int aborted = 0;
        try (DirectoryStream<Path> stale = Files.newDirectoryStream(uploads)) {
            for (final Path scratch : stale) {
                try {
                    if (Files.getLastModifiedTime(scratch).toInstant().isBefore(threshold)
                            && Files.deleteIfExists(scratch)) {
                        aborted++;
                    }
                } catch (final NoSuchFileException e) {
                    // Another sweep won the race; the upload is reclaimed either way.
                }
            }
        } catch (final IOException e) {
            throw new ObjectStoreException("Could not reclaim incomplete uploads", e);
        }
        return aborted;
    }

    /**
     * Resolves a key to a file below {@code objects/}, refusing anything that would leave it.
     *
     * <p>In S3 a key is an opaque string and {@code a/../b} is simply a key. Here it is a path, so the
     * two readings differ and only one of them stays inside the store.
     */
    private Path pathOf(final String key) {
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty");
        }
        if (key.startsWith("/") || key.endsWith("/")) {
            throw new IllegalArgumentException("key must not start or end with '/': " + key);
        }
        Path resolved = objects;
        for (final String segment : key.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || segment.indexOf('\\') >= 0) {
                throw new IllegalArgumentException("key contains an unusable path segment: " + key);
            }
            resolved = resolved.resolve(segment);
        }
        final Path normalised = resolved.normalize();
        if (!normalised.startsWith(objects)) {
            throw new IllegalArgumentException("key escapes the object store: " + key);
        }
        return normalised;
    }

    /** The key a stored file represents — the inverse of {@link #pathOf}. */
    private String keyOf(final Path file) {
        final StringBuilder key = new StringBuilder();
        for (final Path segment : objects.relativize(file)) {
            if (!key.isEmpty()) {
                key.append('/');
            }
            key.append(segment);
        }
        return key.toString();
    }

    private static void moveIntoPlace(final Path scratch, final Path target) throws IOException {
        try {
            Files.move(scratch, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException e) {
            // Some file systems, and some container volume drivers, refuse the atomic flag. The plain
            // move is still a rename within one tree; the guarantee is weaker, but a half-written
            // target is still not reachable under the key.
            Files.move(scratch, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Removes directories the key's slashes created, stopping at the first non-empty one. */
    private void pruneEmptyParents(final @Nullable Path from) throws IOException {
        Path directory = from;
        while (directory != null && directory.startsWith(objects) && !directory.equals(objects)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
                if (entries.iterator().hasNext()) {
                    return;
                }
            } catch (final NoSuchFileException e) {
                return;
            }
            try {
                Files.deleteIfExists(directory);
            } catch (final IOException e) {
                // A concurrent write repopulated it between the check and the delete. Leaving it is
                // correct, and the next deletion below it will try again.
                return;
            }
            directory = directory.getParent();
        }
    }

    private static void deleteQuietly(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (final IOException ignored) {
            // Already unreachable by key; a stale scratch file is what the upload sweep is for.
        }
    }

    private static void closeQuietly(final @Nullable SeekableByteChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (final IOException ignored) {
                // Nothing left to report: the caller is already being handed a failure.
            }
        }
    }

    /** Stops a file stream after {@code limit} bytes, so a ranged read cannot run past its range. */
    private static final class BoundedInputStream extends InputStream {

        private final InputStream delegate;
        private long remaining;

        private BoundedInputStream(final InputStream delegate, final long limit) {
            this.delegate = delegate;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            final int value = delegate.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(final byte[] buffer, final int off, final int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            final int read = delegate.read(buffer, off, (int) Math.min(len, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
