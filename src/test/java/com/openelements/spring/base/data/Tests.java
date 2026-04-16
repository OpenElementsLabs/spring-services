package com.openelements.spring.base.data;

import com.openelements.spring.base.events.GenericDataEvent;
import com.openelements.spring.base.events.OnObjectCreate;
import com.openelements.spring.base.events.OnObjectDelete;
import com.openelements.spring.base.events.OnObjectUpdate;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;

@SpringBootTest(classes = ForTestConfig.class)
public class Tests {

  @Autowired private ForTestDataService forTestDataService;

  @Autowired private TestApplicationListener testApplicationListener;

  @BeforeEach
  void init() {
    forTestDataService.getAll().forEach(dto -> forTestDataService.delete(dto));
  }

  @Test
  void testSaveNew() {
    // given:
    final ForTestDto dto = new ForTestDto(null, "Foo");

    // when:
    final ForTestDto saved = forTestDataService.save(dto);

    // then:
    Assertions.assertNotNull(saved);
    Assertions.assertNotNull(saved.id());
    Assertions.assertEquals("Foo", saved.name());
  }

  @Test
  void testSaveNull() {
    Assertions.assertThrows(NullPointerException.class, () -> forTestDataService.save(null));
  }

  @Test
  void testSaveUnKnown() {
    // given:
    final ForTestDto dto = new ForTestDto(UUID.randomUUID(), "Foo");

    // when:
    Assertions.assertThrows(IllegalArgumentException.class, () -> forTestDataService.save(dto));
  }

  @Test
  void testSaveNewHasReallySaved() {
    // given:
    final ForTestDto dto = new ForTestDto(null, "Foo");

    // when:
    final ForTestDto saved = forTestDataService.save(dto);

    // then:
    Assertions.assertTrue(forTestDataService.existsById(saved.id()));
    Assertions.assertTrue(forTestDataService.findById(saved.id()).isPresent());
    Assertions.assertNotNull(forTestDataService.findAll(Pageable.unpaged()));
    Assertions.assertFalse(forTestDataService.findAll(Pageable.unpaged()).isEmpty());
    Assertions.assertEquals(1, forTestDataService.findAll(Pageable.unpaged()).stream().count());
    Assertions.assertEquals(
        saved, forTestDataService.findAll(Pageable.unpaged()).stream().findAny().orElseThrow());
    Assertions.assertNotNull(forTestDataService.getAll());
    Assertions.assertFalse(forTestDataService.getAll().isEmpty());
    Assertions.assertEquals(1, forTestDataService.getAll().stream().count());
    Assertions.assertEquals(saved, forTestDataService.getAll().stream().findAny().orElseThrow());
  }

  @Test
  void testSaveUpdate() {
    // given:
    final ForTestDto dto = new ForTestDto(null, "Foo");
    final ForTestDto saved = forTestDataService.save(dto);

    // when:
    ForTestDto updated = forTestDataService.save(new ForTestDto(saved.id(), "Bar"));

    Assertions.assertNotNull(updated);
    Assertions.assertNotNull(updated.id());
    Assertions.assertEquals(saved.id(), updated.id());
    Assertions.assertEquals("Bar", updated.name());
    Assertions.assertTrue(forTestDataService.existsById(updated.id()));
    Assertions.assertTrue(forTestDataService.findById(updated.id()).isPresent());
    Assertions.assertEquals(updated, forTestDataService.findById(updated.id()).orElseThrow());
    Assertions.assertEquals(
        updated, forTestDataService.findAll(Pageable.unpaged()).stream().findAny().orElseThrow());
    Assertions.assertEquals(updated, forTestDataService.getAll().stream().findAny().orElseThrow());
  }

  @Test
  void testFindUnknown() {
    Assertions.assertFalse(forTestDataService.existsById(UUID.randomUUID()));
    Assertions.assertFalse(forTestDataService.findById(UUID.randomUUID()).isPresent());
  }

  @Test
  void testDeleteNull() {
    final String nullString = null;
    final UUID nullID = null;
    final ForTestDto nullDto = null;

    Assertions.assertThrows(
        NullPointerException.class, () -> forTestDataService.delete(nullString));
    Assertions.assertThrows(NullPointerException.class, () -> forTestDataService.delete(nullID));
    Assertions.assertThrows(NullPointerException.class, () -> forTestDataService.delete(nullDto));
  }

  @Test
  void testDeleteUnknown() {
    final UUID nullID = UUID.randomUUID();
    final ForTestDto nullDto = new ForTestDto(nullID, "Foo");

    Assertions.assertThrows(
        IllegalArgumentException.class, () -> forTestDataService.delete(nullID));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> forTestDataService.delete(nullDto));
  }

  @Test
  void testDeleteByDto() {
    // given:
    final ForTestDto dto = new ForTestDto(null, "Foo");
    final ForTestDto saved = forTestDataService.save(dto);

    // when:
    forTestDataService.delete(saved);

    // then:
    Assertions.assertFalse(forTestDataService.existsById(saved.id()));
    Assertions.assertFalse(forTestDataService.findById(saved.id()).isPresent());
  }

  @Test
  void testDeleteById() {
    // given:
    final ForTestDto dto = new ForTestDto(null, "Foo");
    final ForTestDto saved = forTestDataService.save(dto);

    // when:
    forTestDataService.delete(saved.id());

    // then:
    Assertions.assertFalse(forTestDataService.existsById(saved.id()));
    Assertions.assertFalse(forTestDataService.findById(saved.id()).isPresent());
  }

  @Test
  void testSaveNewTriggersEvent() {
    // given:
    final ForTestDto dto = new ForTestDto(null, "Foo");
    testApplicationListener.clearEvent();

    // when:
    final ForTestDto saved = forTestDataService.save(dto);
    GenericDataEvent<ForTestDto> forTestDtoGenericDataEvent =
        testApplicationListener.waitForNextEvent();

    // then:
    Assertions.assertNotNull(forTestDtoGenericDataEvent);
    Assertions.assertEquals(saved, forTestDtoGenericDataEvent.getData());
    Assertions.assertTrue(forTestDtoGenericDataEvent instanceof OnObjectCreate<ForTestDto>);
  }

  @Test
  void testSaveUpdateTriggersEvent() {
    // given:
    final ForTestDto dto = new ForTestDto(null, "Foo");
    final ForTestDto saved = forTestDataService.save(dto);
    testApplicationListener.clearEvent();

    // when:
    ForTestDto updated = forTestDataService.save(new ForTestDto(saved.id(), "Bar"));
    GenericDataEvent<ForTestDto> forTestDtoGenericDataEvent =
        testApplicationListener.waitForNextEvent();

    // then:
    Assertions.assertNotNull(forTestDtoGenericDataEvent);
    Assertions.assertEquals(updated, forTestDtoGenericDataEvent.getData());
    Assertions.assertTrue(forTestDtoGenericDataEvent instanceof OnObjectUpdate<ForTestDto>);
  }

  @Test
  void testDeleteTriggersEvent() {
    // given:
    final ForTestDto dto = new ForTestDto(null, "Foo");
    final ForTestDto saved = forTestDataService.save(dto);
    testApplicationListener.clearEvent();

    // when:
    forTestDataService.delete(saved.id());
    GenericDataEvent<ForTestDto> forTestDtoGenericDataEvent =
        testApplicationListener.waitForNextEvent();

    // then:
    Assertions.assertNotNull(forTestDtoGenericDataEvent);
    Assertions.assertEquals(saved, forTestDtoGenericDataEvent.getData());
    Assertions.assertTrue(forTestDtoGenericDataEvent instanceof OnObjectDelete<ForTestDto>);
  }
}
