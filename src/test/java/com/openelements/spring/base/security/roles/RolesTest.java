package com.openelements.spring.base.security.roles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Roles} utility-class constants holder.
 *
 * <h2>What is tested</h2>
 *
 * <p>Two concerns are verified:
 *
 * <ol>
 *   <li>The literal string values of all role constants are stable — downstream applications use
 *       them in {@code @PreAuthorize("hasRole('APP-ADMIN')")} expressions and on the IdP side
 *       (Authentik group → JWT {@code roles} claim mapping), so a silent rename would break
 *       authorization at runtime.
 *   <li>The class is a properly-locked utility class — {@code final}, with a {@code private}
 *       constructor that prevents accidental instantiation through ordinary means but stays
 *       reachable via {@code setAccessible(true)} for serialization frameworks that need it.
 * </ol>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 unit tests, no Spring context, no mocks. The constants are asserted by literal
 * equality; the constructor visibility and reflection behaviour are checked via {@code
 * java.lang.reflect.Constructor}. No collaborator exists for {@code Roles}, so there is nothing
 * to mock and nothing to stub.
 */
class RolesTest {

  /**
   * Asserts each {@code ROLE_*} constant resolves to its documented literal value — guards
   * against accidental renames that would break consumer-side {@code hasRole(...)} checks and
   * IdP-side claim mappings.
   */
  @Test
  @DisplayName("All Roles.ROLE_* constants resolve to their documented literal string values.")
  void constantValuesAreStable() {
    assertEquals("APP-ADMIN", Roles.ROLE_APP_ADMIN);
    assertEquals("APP-USER", Roles.ROLE_APP_USER);
    assertEquals("IT-ADMIN", Roles.ROLE_IT_ADMIN);
    assertEquals("EMPLOYEE", Roles.ROLE_EMPLOYEE);
    assertEquals("EXTERNAL", Roles.ROLE_EXTERNAL);
    assertEquals("BACKOFFICE", Roles.ROLE_BACKOFFICE);
    assertEquals("MANAGEMENT", Roles.ROLE_MANAGEMENT);
  }

  /**
   * Verifies the utility-class lock: {@code Roles} declares a {@code private} no-arg constructor.
   * Static analysers (SpotBugs, PMD) raise a finding if a constants holder is instantiable.
   */
  @Test
  @DisplayName("Roles declares a private no-arg constructor so static analysers accept it as a utility class.")
  void constructorIsPrivate() throws NoSuchMethodException {
    final Constructor<Roles> constructor = Roles.class.getDeclaredConstructor();
    assertTrue(
        Modifier.isPrivate(constructor.getModifiers()),
        "Roles constructor must be private to lock the utility class");
  }

  /**
   * Confirms ordinary reflection cannot instantiate {@code Roles} — {@code newInstance()}
   * without {@code setAccessible(true)} throws {@link IllegalAccessException}. Documents that
   * the private constructor is real protection against casual misuse.
   */
  @Test
  @DisplayName("Reflective newInstance() on Roles throws IllegalAccessException without setAccessible(true).")
  void reflectiveInstantiationRequiresSetAccessible() throws NoSuchMethodException {
    final Constructor<Roles> constructor = Roles.class.getDeclaredConstructor();
    assertThrows(IllegalAccessException.class, constructor::newInstance);
  }

  /**
   * Documents the boundary of the private-constructor lock: with {@code setAccessible(true)} a
   * caller <em>can</em> instantiate {@code Roles} — Java reflection can always bypass access
   * modifiers. The lock is a hygiene marker for static analysis, not a runtime security
   * guarantee. This test exists so future readers do not misinterpret the lock as enforced.
   */
  @Test
  @DisplayName("setAccessible(true) still bypasses the private constructor — the lock is hygiene, not security.")
  void reflectiveInstantiationStillPossibleWithSetAccessible()
      throws NoSuchMethodException,
          InstantiationException,
          IllegalAccessException,
          InvocationTargetException {
    final Constructor<Roles> constructor = Roles.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    final Roles instance = constructor.newInstance();
    assertTrue(instance != null);
  }
}
