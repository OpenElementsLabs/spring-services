package com.openelements.spring.base.services.storage.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.openelements.spring.base.services.storage.ObjectStore;
import com.openelements.spring.base.services.storage.ObjectStoreContractTest;
import com.openelements.spring.base.services.storage.ObjectStoreException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The {@link ObjectStore} contract against the file-system store, plus the three promises this
 * implementation makes on its own: a key cannot escape the root, a failed write leaves the previous
 * object in place, and an interrupted write is reclaimable.
 */
@DisplayName("FileObjectStore")
class FileObjectStoreTest extends ObjectStoreContractTest {

    @TempDir
    Path root;

    private FileObjectStore store;

    @BeforeEach
    void setUp() {
        store = new FileObjectStore(root);
    }

    @Override
    protected ObjectStore store() {
        return store;
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"../escape.txt", "a/../../escape.txt", "/absolute.txt", "trailing/",
            "a//b.txt", "a/./b.txt", "a\\b.txt", ""})
    @DisplayName("A key that would leave the store is rejected")
    void keysCannotEscapeTheRoot(final String key) {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> store.get(key, 0, 1));
    }

    @Test
    @DisplayName("Nothing is written outside the store's own directory")
    void rejectedKeysWriteNothing() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> put("../escape.txt", "payload"));

        assertThat(root.getParent().resolve("escape.txt")).doesNotExist();
    }

    @Test
    @DisplayName("A write that fails mid-stream leaves the previous object untouched")
    void failedWriteKeepsThePreviousObject() throws IOException {
        put("fragile.txt", "original");

        assertThatExceptionOfType(ObjectStoreException.class)
                .isThrownBy(() -> store.put("fragile.txt", failingStream(), "text/plain"));

        assertThat(readAll(store.get("fragile.txt"))).isEqualTo("original");
    }

    @Test
    @DisplayName("A write that fails leaves no scratch file behind")
    void failedWriteLeavesNoScratchFile() throws IOException {
        assertThatExceptionOfType(ObjectStoreException.class)
                .isThrownBy(() -> store.put("fragile.txt", failingStream(), "text/plain"));

        try (Stream<Path> uploads = Files.list(root.resolve("uploads"))) {
            assertThat(uploads).isEmpty();
        }
    }

    @Test
    @DisplayName("An interrupted upload is invisible to list but reclaimed by the sweep")
    void interruptedUploadsAreReclaimed() throws IOException {
        final Path scratch = Files.writeString(root.resolve("uploads").resolve("stale.part"), "x");
        Files.setLastModifiedTime(scratch, FileTime.from(Instant.now().minusSeconds(3600)));

        assertThat(store.list("").toList()).isEmpty();
        assertThat(store.abortIncompleteUploadsOlderThan(Instant.now().minusSeconds(60))).isOne();
        assertThat(scratch).doesNotExist();
    }

    @Test
    @DisplayName("A recent upload is left alone by the sweep")
    void recentUploadsSurviveTheSweep() throws IOException {
        final Path scratch = Files.writeString(root.resolve("uploads").resolve("fresh.part"), "x");

        assertThat(store.abortIncompleteUploadsOlderThan(Instant.now().minusSeconds(3600))).isZero();
        assertThat(scratch).exists();
    }

    @Test
    @DisplayName("Deleting the last object under a prefix removes the directory it created")
    void deletePrunesEmptyDirectories() {
        put("nested/deep/object.txt", "payload");

        assertThat(store.delete("nested/deep/object.txt")).isTrue();
        assertThat(root.resolve("objects").resolve("nested")).doesNotExist();
        assertThat(root.resolve("objects")).exists();
    }

    /** A source that fails after the first byte, standing in for a connection that drops. */
    private static InputStream failingStream() {
        return new InputStream() {
            private boolean served;

            @Override
            public int read() throws IOException {
                if (served) {
                    throw new IOException("source failed mid-stream");
                }
                served = true;
                return 'x';
            }
        };
    }

    @Test
    @DisplayName("The content type is accepted and deliberately not stored — no sidecar file appears")
    void contentTypeIsNotPersisted() throws IOException {
        store.put("typed.txt", new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)),
                "image/png");

        try (Stream<Path> stored = Files.walk(root.resolve("objects"))) {
            assertThat(stored.filter(Files::isRegularFile))
                    .as("one object means one file; a remembered content type would need a second")
                    .singleElement()
                    .satisfies(file -> assertThat(file.getFileName()).hasToString("typed.txt"));
        }
    }
}
