package com.openelements.spring.base.services.storage.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.services.storage.ObjectStore;
import com.openelements.spring.base.services.storage.ObjectStoreContractTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The {@link ObjectStore} contract, against the in-memory fake. */
@DisplayName("InMemoryObjectStore")
class InMemoryObjectStoreTest extends ObjectStoreContractTest {

    private InMemoryObjectStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryObjectStore();
    }

    @Override
    protected ObjectStore store() {
        return store;
    }

    @Test
    @DisplayName("list reports when an object was written, not when it was listed")
    void listReportsTheWriteTime() {
        final Instant before = Instant.now();
        put("aged.txt", "content");

        assertThat(store.list("").toList())
                .singleElement()
                .satisfies(object -> assertThat(object.lastModified())
                        .isAfterOrEqualTo(before.minusSeconds(1))
                        .isBeforeOrEqualTo(Instant.now().plusSeconds(1)));
    }

    @Test
    @DisplayName("The fake has no multipart uploads to reclaim")
    void nothingToAbort() {
        assertThat(store.abortIncompleteUploadsOlderThan(Instant.now())).isZero();
    }
}
