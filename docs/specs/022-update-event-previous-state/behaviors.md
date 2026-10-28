# Behaviors: Previous state on update events

Scenarios are unit-level unless marked **[context]** (a real Spring `ApplicationContext` dispatching
the event).

## `OnObjectUpdate` construction

### An update event without a previous state

- **Given** a DTO `current`
- **When** `new OnObjectUpdate<>(current)` is created
- **Then** `getData()` returns `current`
- **And** `isPreviousKnown()` is `false`
- **And** `getPrevious()` is empty

### An update event with a previous state

- **Given** two DTOs `previous` and `current` of the same class with the same id
- **When** `new OnObjectUpdate<>(previous, current)` is created
- **Then** `getData()` returns `current`
- **And** `isPreviousKnown()` is `true`
- **And** `getPrevious()` contains `previous`

### A previous state equal to the current state is accepted

- **Given** `previous` and `current` that are equal
- **When** `new OnObjectUpdate<>(previous, current)` is created
- **Then** the event is created — suppressing no-ops is not the event's job

### A null previous state is rejected

- **When** `new OnObjectUpdate<>(null, current)` is created
- **Then** a `NullPointerException` is thrown

### A previous state with a different id is rejected

- **Given** `previous` and `current` of the same class with different ids
- **When** `new OnObjectUpdate<>(previous, current)` is created
- **Then** an `IllegalArgumentException` is thrown

### A previous state of a different class is rejected

- **Given** `previous` and `current` of different classes with the same id
- **When** `new OnObjectUpdate<>(previous, current)` is created
- **Then** an `IllegalArgumentException` is thrown

## Typed listeners

### The resolvable type carries the DTO type

- **Given** an `OnObjectUpdate` for a `BookDto`
- **When** `getResolvableType()` is called
- **Then** the result is `OnObjectUpdate<BookDto>`

### A subclass that fixes the type parameter still resolves

- **Given** an application event `class BookUpdated extends OnObjectUpdate<BookDto>`
- **When** `getResolvableType()` is called on an instance
- **Then** the result is `BookUpdated` and no exception is thrown

### A typed listener ignores events for other DTO types [context]

- **Given** a listener method `onBook(OnObjectUpdate<BookDto>)`
- **When** an `OnObjectUpdate<AuthorDto>` is published
- **Then** `onBook` is not invoked

### A typed listener receives events for its DTO type [context]

- **Given** a listener method `onBook(OnObjectUpdate<BookDto>)`
- **When** an `OnObjectUpdate<BookDto>` is published
- **Then** `onBook` is invoked once with that event

### A listener on the base type receives every DTO type [context]

- **Given** a listener method `onAny(GenericDataEvent<?>)`
- **When** an `OnObjectUpdate<BookDto>` and an `OnObjectCreate<AuthorDto>` are published
- **Then** `onAny` is invoked for both

## `AbstractDbBackedDataService`

### An insert publishes a create event

- **When** `save(dto)` is called with a DTO without id
- **Then** exactly one `OnObjectCreate` carrying the saved DTO is published

### An update publishes previous and current state

- **Given** a persisted entity whose name is `"old"`
- **When** `save(dto)` is called with the same id and name `"new"`
- **Then** exactly one `OnObjectUpdate` is published
- **And** its `getPrevious()` has name `"old"`
- **And** its `getData()` has name `"new"`

### An update that changes nothing publishes no event

- **Given** a persisted entity whose name is `"same"`
- **When** `save(dto)` is called with the same id and name `"same"`
- **Then** no event is published
- **And** the saved DTO is returned as usual

### Events stay suppressed when publishing is disabled

- **Given** a service constructed with `publishEvents == false`
- **When** an insert, a real update and a delete are performed
- **Then** no event is published

## `AbstractMultitenantDbBackedDataService`

### An update publishes previous and current state

- **Given** a persisted entity of the current tenant whose name is `"old"`
- **When** `save(dto)` is called with the same id and name `"new"`
- **Then** exactly one `OnObjectUpdate` is published with previous name `"old"` and current name
  `"new"`

### An update that changes nothing publishes no event

- **Given** a persisted entity of the current tenant whose name is `"same"`
- **When** `save(dto)` is called with the same id and name `"same"`
- **Then** no event is published
