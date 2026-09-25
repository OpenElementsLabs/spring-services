package com.openelements.spring.base.services.storage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The behaviour every {@link ObjectStore} must show, run against each implementation.
 *
 * <p>It exists because the implementations disagreed. {@code ObjectStore} promises that a range past
 * the end returns the available bytes rather than failing; the file store did that and the S3 store
 * answered with the SDK's own exception, so the same call gave two different answers depending on
 * configuration. A shared suite is the only thing that keeps three backends honest about one
 * interface.
 *
 * <p>Only portable keys are used here. A key is an opaque string on S3 and a path in the file store,
 * so {@code a/../b} means different things to them — a deliberate divergence, covered in
 * {@code FileObjectStoreTest} rather than smuggled into the shared contract.
 */
public abstract class ObjectStoreContractTest {

    /** The store under test, fresh per test method. */
    protected abstract ObjectStore store();

    @Nested
    @DisplayName("whole-object reads and writes")
    class WholeObject {

        @Test
        @DisplayName("An object reads back exactly as written")
        void roundTrip() throws IOException {
            put("round/trip.txt", "hello");

            assertThat(readAll(store().get("round/trip.txt"))).isEqualTo("hello");
        }

        @Test
        @DisplayName("Writing the same key again replaces the object")
        void overwrite() throws IOException {
            put("overwrite.txt", "first");
            put("overwrite.txt", "second");

            assertThat(readAll(store().get("overwrite.txt"))).isEqualTo("second");
        }

        @Test
        @DisplayName("An empty payload is a valid object, not a missing one")
        void emptyObject() throws IOException {
            put("empty.txt", "");

            assertThat(readAll(store().get("empty.txt"))).isEmpty();
            assertThat(store().size("empty.txt")).hasValue(0L);
        }

        @Test
        @DisplayName("Reading a key that has no object throws ObjectNotFoundException")
        void missingKey() {
            assertThatExceptionOfType(ObjectNotFoundException.class)
                    .isThrownBy(() -> store().get("absent.txt"));
        }
    }

    @Nested
    @DisplayName("ranged reads")
    class RangedReads {

        @Test
        @DisplayName("A range inside the object returns exactly that slice")
        void sliceInsideTheObject() throws IOException {
            put("ranged.txt", "0123456789");

            assertThat(readAll(store().get("ranged.txt", 2, 4))).isEqualTo("2345");
        }

        @Test
        @DisplayName("A range extending past the end returns the available bytes")
        void rangeExtendingPastTheEnd() throws IOException {
            put("ranged.txt", "0123456789");

            assertThat(readAll(store().get("ranged.txt", 7, 100))).isEqualTo("789");
        }

        @Test
        @DisplayName("A range starting exactly at the end returns nothing")
        void rangeStartingAtTheEnd() throws IOException {
            put("ranged.txt", "0123456789");

            assertThat(readAll(store().get("ranged.txt", 10, 5))).isEmpty();
        }

        @Test
        @DisplayName("A range starting past the end returns nothing rather than failing")
        void rangeStartingPastTheEnd() throws IOException {
            put("ranged.txt", "0123456789");

            assertThat(readAll(store().get("ranged.txt", 50, 5))).isEmpty();
        }

        @Test
        @DisplayName("A length of Long.MAX_VALUE reads to the end instead of overflowing")
        void readToTheEnd() throws IOException {
            put("ranged.txt", "0123456789");

            assertThat(readAll(store().get("ranged.txt", 4, Long.MAX_VALUE))).isEqualTo("456789");
        }

        @Test
        @DisplayName("A zero-length range of an existing object returns nothing")
        void zeroLengthOfAnExistingObject() throws IOException {
            put("ranged.txt", "0123456789");

            assertThat(readAll(store().get("ranged.txt", 0, 0))).isEmpty();
        }

        @Test
        @DisplayName("A zero-length range of a missing key is a wrong key, not 'no bytes'")
        void zeroLengthOfAMissingKey() {
            assertThatExceptionOfType(ObjectNotFoundException.class)
                    .isThrownBy(() -> store().get("absent.txt", 0, 0));
        }

        @Test
        @DisplayName("A ranged read of a missing key throws ObjectNotFoundException")
        void rangedReadOfAMissingKey() {
            assertThatExceptionOfType(ObjectNotFoundException.class)
                    .isThrownBy(() -> store().get("absent.txt", 0, 10));
        }

        @Test
        @DisplayName("A negative offset is rejected")
        void negativeOffset() {
            put("ranged.txt", "0123456789");

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> store().get("ranged.txt", -1, 5));
        }

        @Test
        @DisplayName("A negative length is rejected")
        void negativeLength() {
            put("ranged.txt", "0123456789");

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> store().get("ranged.txt", 0, -1));
        }
    }

    @Nested
    @DisplayName("size, delete and list")
    class Metadata {

        @Test
        @DisplayName("size is empty for a key that has no object")
        void sizeOfAMissingKey() {
            assertThat(store().size("absent.txt")).isEmpty();
        }

        @Test
        @DisplayName("size reports the byte count of a stored object")
        void sizeOfAStoredObject() {
            put("sized.txt", "0123456789");

            assertThat(store().size("sized.txt")).hasValue(10L);
        }

        @Test
        @DisplayName("delete removes the object and reports that it did")
        void deleteRemoves() {
            put("doomed.txt", "bye");

            assertThat(store().delete("doomed.txt")).isTrue();
            assertThat(store().size("doomed.txt")).isEmpty();
        }

        @Test
        @DisplayName("Deleting a key that has no object succeeds and reports nothing removed")
        void deleteIsIdempotent() {
            assertThat(store().delete("absent.txt")).isFalse();
        }

        @Test
        @DisplayName("list returns the objects under a prefix and nothing else")
        void listByPrefix() {
            put("wanted/a.txt", "aa");
            put("wanted/b.txt", "bbbb");
            put("other/c.txt", "cccccc");

            final List<StoredObject> found = store().list("wanted/").toList();

            assertThat(found).extracting(StoredObject::key)
                    .containsExactlyInAnyOrder("wanted/a.txt", "wanted/b.txt");
            assertThat(found).extracting(StoredObject::size)
                    .containsExactlyInAnyOrder(2L, 4L);
        }

        @Test
        @DisplayName("list returns nothing for a prefix no object matches")
        void listWithoutMatches() {
            put("wanted/a.txt", "aa");

            assertThat(store().list("nothing/").toList()).isEmpty();
        }
    }

    /** Writes a UTF-8 payload under a key. */
    protected void put(final String key, final String content) {
        store().put(key, new ByteArrayInputStream(content.getBytes(UTF_8)), "text/plain");
    }

    /** Reads a stream to the end and closes it — the contract hands ownership to the caller. */
    protected static String readAll(final InputStream in) throws IOException {
        try (in) {
            return new String(in.readAllBytes(), UTF_8);
        }
    }
}
