package com.openelements.spring.base.events;

import com.openelements.spring.base.data.WithId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenericDataEventTest {

    private record TestData(UUID id, String value) implements WithId {
    }

    @Nested
    @DisplayName("OnObjectCreate")
    class OnObjectCreateTest {

        @Test
        void shouldStoreSourceAndDeriveType() {
            //GIVEN
            final TestData data = new TestData(UUID.randomUUID(), "test");

            //WHEN
            final OnObjectCreate<TestData> event = new OnObjectCreate<>(data);

            //THEN
            assertThat(event.getSource()).isSameAs(data);
            assertThat(event.getData()).isSameAs(data);
            assertThat(event.getType()).isEqualTo(TestData.class);
            assertThat(event.entityId()).isEqualTo(data.id());
        }

        @Test
        void shouldRejectNullSource() {
            assertThatThrownBy(() -> new OnObjectCreate<TestData>(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("OnObjectUpdate")
    class OnObjectUpdateTest {

        @Test
        void shouldStoreSourceAndDeriveType() {
            //GIVEN
            final TestData data = new TestData(UUID.randomUUID(), "updated");

            //WHEN
            final OnObjectUpdate<TestData> event = new OnObjectUpdate<>(data);

            //THEN
            assertThat(event.getSource()).isSameAs(data);
            assertThat(event.getType()).isEqualTo(TestData.class);
        }
    }

    @Nested
    @DisplayName("OnObjectDelete")
    class OnObjectDeleteTest {

        @Test
        void shouldStoreSourceAndDeriveType() {
            //GIVEN
            final TestData data = new TestData(UUID.randomUUID(), "deleted");

            //WHEN
            final OnObjectDelete<TestData> event = new OnObjectDelete<>(data);

            //THEN
            assertThat(event.getSource()).isSameAs(data);
            assertThat(event.getType()).isEqualTo(TestData.class);
        }
    }

    @Nested
    @DisplayName("GenericDataEvent with explicit type")
    class ExplicitTypeTest {

        @Test
        void shouldUseExplicitTypeWhenProvided() {
            //GIVEN
            final TestData data = new TestData(UUID.randomUUID(), "typed");

            //WHEN
            final OnObjectCreate<TestData> event = new OnObjectCreate<>(data);

            //THEN
            assertThat(event.getType()).isEqualTo(TestData.class);
        }
    }
}