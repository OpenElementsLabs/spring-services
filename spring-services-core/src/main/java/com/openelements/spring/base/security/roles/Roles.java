package com.openelements.spring.base.security.roles;

/** Predefined roles. */
public final class Roles {

  private Roles() {}

  /** Predefined role for administrators. */
  public static final String ROLE_APP_ADMIN = "APP-ADMIN";

  /** Predefined role for regular users. */
  public static final String ROLE_APP_USER = "APP-USER";

  /** Predefined role for IT administrators. */
  public static final String ROLE_IT_ADMIN = "IT-ADMIN";

  /** Predefined role for internal employees. */
  public static final String ROLE_EMPLOYEE = "EMPLOYEE";

  /** Predefined role for external, non-employee users. */
  public static final String ROLE_EXTERNAL = "EXTERNAL";

  /** Predefined role for back-office staff. */
  public static final String ROLE_BACKOFFICE = "BACKOFFICE";

  /** Predefined role for management users. */
  public static final String ROLE_MANAGEMENT = "MANAGEMENT";
}
