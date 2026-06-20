package com.openelements.spring.base.security.roles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class RolesTest {

  @Test
  void constantValuesAreStable() {
    assertEquals("APP-ADMIN", Roles.ROLE_APP_ADMIN);
    assertEquals("APP-USER", Roles.ROLE_APP_USER);
    assertEquals("IT-ADMIN", Roles.ROLE_IT_ADMIN);
    assertEquals("EMPLOYEE", Roles.ROLE_EMPLOYEE);
    assertEquals("EXTERNAL", Roles.ROLE_EXTERNAL);
    assertEquals("BACKOFFICE", Roles.ROLE_BACKOFFICE);
    assertEquals("MANAGEMENT", Roles.ROLE_MANAGEMENT);
  }

  @Test
  void constructorIsPrivate() throws NoSuchMethodException {
    final Constructor<Roles> constructor = Roles.class.getDeclaredConstructor();
    assertTrue(
        Modifier.isPrivate(constructor.getModifiers()),
        "Roles constructor must be private to lock the utility class");
  }

  @Test
  void reflectiveInstantiationRequiresSetAccessible() throws NoSuchMethodException {
    final Constructor<Roles> constructor = Roles.class.getDeclaredConstructor();
    assertThrows(IllegalAccessException.class, constructor::newInstance);
  }

  @Test
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
