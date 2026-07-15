package com.openelements.spring.base.services.comment;

import com.openelements.spring.base.services.user.UserConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Spring configuration for the comment feature.
 *
 * <p>Component-scans this package to register the comment service and repository, and imports the
 * user configuration on which the comment authorship depends.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = CommentConfig.class)
@Import(UserConfig.class)
public class CommentConfig {

  /** Creates the configuration; the comment beans are registered via component scanning. */
  public CommentConfig() {}
}
