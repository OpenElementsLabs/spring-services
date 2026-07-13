package com.openelements.spring.base.data;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Dependency-free convention guard for the library's JPA entities. Uses Spring's classpath scanner
 * (already on the compile classpath — no new dependency such as ArchUnit) plus reflection, so it
 * runs as a fast unit test without a Spring context or a database.
 *
 * <p>Two invariants are enforced for every {@code @Entity} under {@link #BASE_PACKAGE}:
 *
 * <ol>
 *   <li><b>Dedicated schema.</b> Each entity must declare {@code @Table(schema = DbSchema.NAME)}.
 *       This is the self-enforcing half of the dedicated-schema design: any future entity that
 *       forgets the schema (and would silently land in {@code public}) fails the build here.
 *   <li><b>No lib → app association.</b> A library entity must never map a JPA association
 *       ({@code @ManyToOne}, {@code @OneToOne}, {@code @OneToMany}, {@code @ManyToMany}) to a type
 *       outside the library package. Applications may reference library entities, never the reverse
 *       (the app ↔ lib cross-entity contract).
 * </ol>
 */
class EntitySchemaConventionTest {

  private static final String BASE_PACKAGE = "com.openelements.spring.base";

  /**
   * The seven entities that must exist today. Pinning the exact set guards against a silently
   * empty/misconfigured scan that would let the assertions pass vacuously.
   */
  private static final Set<String> EXPECTED_ENTITY_SIMPLE_NAMES =
      Set.of(
          "UserEntity",
          "ApiKeyEntity",
          "AuditLogEntity",
          "CommentEntity",
          "SettingsEntity",
          "TagEntity",
          "WebhookEntity");

  private static List<Class<?>> scanLibraryEntities() {
    final ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    final List<Class<?>> entities = new ArrayList<>();
    scanner
        .findCandidateComponents(BASE_PACKAGE)
        .forEach(
            def -> {
              try {
                final Class<?> entity = Class.forName(def.getBeanClassName());
                if (isProductionClass(entity)) {
                  entities.add(entity);
                }
              } catch (final ClassNotFoundException e) {
                throw new IllegalStateException("Scanned entity not loadable: " + def, e);
              }
            });
    return entities;
  }

  /**
   * The convention applies to the library's <em>shipped</em> entities only. Test-fixture entities
   * (compiled into {@code target/test-classes}) live under the same base package but are deliberately
   * schema-less, so they are filtered out by code-source location.
   */
  private static boolean isProductionClass(final Class<?> type) {
    final var codeSource = type.getProtectionDomain().getCodeSource();
    if (codeSource == null || codeSource.getLocation() == null) {
      return true;
    }
    return !codeSource.getLocation().getPath().contains("test-classes");
  }

  @Test
  @DisplayName("The classpath scan finds exactly the seven known library entities (no vacuous pass)")
  void scanFindsAllSevenEntities() {
    final List<Class<?>> entities = scanLibraryEntities();

    assertThat(entities).extracting(Class::getSimpleName)
        .containsExactlyInAnyOrderElementsOf(EXPECTED_ENTITY_SIMPLE_NAMES);
  }

  @Test
  @DisplayName("Every library @Entity declares @Table(schema = DbSchema.NAME)")
  void everyEntityIsMappedToTheDedicatedSchema() {
    for (final Class<?> entity : scanLibraryEntities()) {
      final Table table = entity.getAnnotation(Table.class);
      assertThat(table)
          .as("%s must carry a @Table annotation", entity.getName())
          .isNotNull();
      assertThat(table.schema())
          .as("%s must map to the dedicated schema", entity.getName())
          .isEqualTo(DbSchema.NAME);
    }
  }

  @Test
  @DisplayName("No library entity maps a JPA association to a type outside the library package")
  void noLibraryEntityAssociatesToANonLibraryType() {
    for (final Class<?> entity : scanLibraryEntities()) {
      for (Class<?> type = entity; type != null && type != Object.class;
          type = type.getSuperclass()) {
        for (final Field field : type.getDeclaredFields()) {
          if (!isJpaAssociation(field)) {
            continue;
          }
          final Class<?> target = associationTargetType(field);
          assertThat(target.getName())
              .as(
                  "%s.%s associates to %s, which is outside the library package",
                  entity.getName(), field.getName(), target.getName())
              .startsWith(BASE_PACKAGE);
        }
      }
    }
  }

  private static boolean isJpaAssociation(final Field field) {
    for (final Class<? extends Annotation> association :
        List.of(ManyToOne.class, OneToOne.class, OneToMany.class, ManyToMany.class)) {
      if (field.isAnnotationPresent(association)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Resolves the entity type an association points at: the field type for {@code *ToOne}, or the
   * element type of a {@code Collection<T>} for {@code *ToMany}.
   */
  private static Class<?> associationTargetType(final Field field) {
    if (field.isAnnotationPresent(OneToMany.class) || field.isAnnotationPresent(ManyToMany.class)) {
      final Type generic = field.getGenericType();
      if (generic instanceof ParameterizedType parameterized
          && parameterized.getActualTypeArguments().length == 1
          && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
        return element;
      }
    }
    return field.getType();
  }
}
