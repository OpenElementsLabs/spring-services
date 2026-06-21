package com.openelements.spring.base.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openelements.spring.base.data.WithId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the three concrete lifecycle event records that extend {@link GenericDataEvent}:
 * {@link OnObjectCreate}, {@link OnObjectUpdate}, {@link OnObjectDelete}.
 *
 * <h2>What is tested</h2>
 *
 * <p>The events are tiny value carriers. The contract: each event preserves its source object
 * (returnable via {@code getSource()} and {@code getData()}), derives the event's DTO type from
 * the source's runtime class, exposes the source's id via {@code entityId()}, and rejects null
 * sources at construction time. The {@code ExplicitTypeTest} nested class is a regression guard
 * for the type-derivation behaviour, redundant on the happy path but reads as documentation.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5, no mocks. A local record {@code TestData} stands in for any {@link WithId}
 * domain type — the events are generic so the concrete type does not matter beyond its
 * non-nullness.
 *
 * <p><b>Mock-Audit.</b> Zero mocks; nothing to mock — the events have no external collaborators.
 */
class GenericDataEventTest {

  private record TestData(UUID id, String value) implements WithId {}

  @Nested
  @DisplayName("OnObjectCreate")
  class OnObjectCreateTest {

    @Test
    @DisplayName("The event stores the source DTO and derives the type from its runtime class.")
    void shouldStoreSourceAndDeriveType() {
      // GIVEN
      final TestData data = new TestData(UUID.randomUUID(), "test");

      // WHEN
      final OnObjectCreate<TestData> event = new OnObjectCreate<>(data);

      // THEN
      assertThat(event.getSource()).isSameAs(data);
      assertThat(event.getData()).isSameAs(data);
      assertThat(event.getType()).isEqualTo(TestData.class);
      assertThat(event.entityId()).isEqualTo(data.id());
    }

    @Test
    @DisplayName("OnObjectCreate rejects a null source DTO with NullPointerException.")
    void shouldRejectNullSource() {
      assertThatThrownBy(() -> new OnObjectCreate<TestData>(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("OnObjectUpdate")
  class OnObjectUpdateTest {

    @Test
    @DisplayName("The event stores the source DTO and derives the type from its runtime class.")
    void shouldStoreSourceAndDeriveType() {
      // GIVEN
      final TestData data = new TestData(UUID.randomUUID(), "updated");

      // WHEN
      final OnObjectUpdate<TestData> event = new OnObjectUpdate<>(data);

      // THEN
      assertThat(event.getSource()).isSameAs(data);
      assertThat(event.getType()).isEqualTo(TestData.class);
    }
  }

  @Nested
  @DisplayName("OnObjectDelete")
  class OnObjectDeleteTest {

    @Test
    @DisplayName("The event stores the source DTO and derives the type from its runtime class.")
    void shouldStoreSourceAndDeriveType() {
      // GIVEN
      final TestData data = new TestData(UUID.randomUUID(), "deleted");

      // WHEN
      final OnObjectDelete<TestData> event = new OnObjectDelete<>(data);

      // THEN
      assertThat(event.getSource()).isSameAs(data);
      assertThat(event.getType()).isEqualTo(TestData.class);
    }
  }

  @Nested
  @DisplayName("GenericDataEvent with explicit type")
  class ExplicitTypeTest {

    @Test
    @DisplayName("getType() returns the concrete source class — documents the type-derivation contract.")
    void shouldUseExplicitTypeWhenProvided() {
      // GIVEN
      final TestData data = new TestData(UUID.randomUUID(), "typed");

      // WHEN
      final OnObjectCreate<TestData> event = new OnObjectCreate<>(data);

      // THEN
      assertThat(event.getType()).isEqualTo(TestData.class);
    }
  }
}
