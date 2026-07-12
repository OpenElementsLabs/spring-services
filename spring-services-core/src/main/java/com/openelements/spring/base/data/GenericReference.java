package com.openelements.spring.base.data;

import java.util.UUID;
import org.jspecify.annotations.NonNull;

/** Represents a generic reference with an identifier and type. */
public interface GenericReference {

  /**
   * The identifier of the referenced object.
   *
   * @return the reference id
   */
  @NonNull UUID referenceId();

  /**
   * The type of the referenced object.
   *
   * @return the reference type
   */
  @NonNull String referenceType();
}
