package com.openelements.spring.base.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.data.WithId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.EventListener;

/**
 * Verifies that Spring dispatches lifecycle events only to listeners declared for the matching DTO
 * type.
 *
 * <h2>What is tested</h2>
 *
 * <p>Generic type erasure hides the DTO type of an event instance. Without {@link
 * GenericDataEvent#getResolvableType()}, Spring treats the unresolvable type parameter as a wildcard
 * and invokes a listener for {@code OnObjectUpdate<BookDto>} with an {@code
 * OnObjectUpdate<AuthorDto>} too. These tests pin the corrected dispatch.
 *
 * <h2>How it is tested</h2>
 *
 * <p>A minimal {@link AnnotationConfigApplicationContext} with one listener bean; events are
 * published through the context, exactly as the data services publish them.
 *
 * <p><b>Mock-Audit.</b> Zero mocks — the unit under test is Spring's real event dispatch.
 */
@DisplayName("Typed lifecycle event listeners")
class TypedEventListenerTest {

  record BookDto(UUID id) implements WithId {}

  record AuthorDto(UUID id) implements WithId {}

  static class RecordingListener {

    final List<Object> books = new ArrayList<>();

    final List<Object> any = new ArrayList<>();

    @EventListener
    public void onBook(final OnObjectUpdate<BookDto> event) {
      books.add(event.getData());
    }

    @EventListener
    public void onAny(final GenericDataEvent<?> event) {
      any.add(event.getData());
    }
  }

  private AnnotationConfigApplicationContext context;

  private RecordingListener listener;

  @BeforeEach
  void startContext() {
    context = new AnnotationConfigApplicationContext();
    context.registerBean(RecordingListener.class);
    context.refresh();
    listener = context.getBean(RecordingListener.class);
  }

  @AfterEach
  void closeContext() {
    context.close();
  }

  @Test
  @DisplayName("A listener for OnObjectUpdate<BookDto> is not invoked for an author update.")
  void shouldIgnoreEventsForOtherDtoTypes() {
    // WHEN
    context.publishEvent(new OnObjectUpdate<>(new AuthorDto(UUID.randomUUID())));

    // THEN
    assertThat(listener.books).isEmpty();
  }

  @Test
  @DisplayName("A listener for OnObjectUpdate<BookDto> is invoked once for a book update.")
  void shouldReceiveEventsForItsDtoType() {
    // GIVEN
    final BookDto book = new BookDto(UUID.randomUUID());

    // WHEN
    context.publishEvent(new OnObjectUpdate<>(book));

    // THEN
    assertThat(listener.books).containsExactly(book);
  }

  @Test
  @DisplayName("A listener for GenericDataEvent<?> is invoked for every event type and DTO type.")
  void shouldDispatchEveryEventToWildcardListener() {
    // GIVEN
    final BookDto book = new BookDto(UUID.randomUUID());
    final AuthorDto author = new AuthorDto(UUID.randomUUID());

    // WHEN
    context.publishEvent(new OnObjectUpdate<>(book));
    context.publishEvent(new OnObjectCreate<>(author));

    // THEN
    assertThat(listener.any).containsExactly(book, author);
  }
}
