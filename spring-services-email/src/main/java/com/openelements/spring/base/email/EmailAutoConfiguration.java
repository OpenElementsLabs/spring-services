package com.openelements.spring.base.email;

import com.openelements.spring.base.services.email.EmailConfig;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for the optional SMTP email feature.
 *
 * <p>Registered through this module's
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, so the
 * feature self-activates when {@code spring-services-email} is on the classpath.
 * {@link ConditionalOnClass @ConditionalOnClass(MimeMessage.class)} keeps it inert if the
 * {@code spring-boot-starter-mail} dependency is not present, so a bundle that ships this class
 * without the mail library causes no {@code NoClassDefFoundError}.
 *
 * <p>Lives outside the {@code services.email} package on purpose: {@link EmailConfig} declares a
 * {@code @ComponentScan} over its own package, and an auto-configuration co-located there would be
 * swept up by that scan and registered twice. {@code EmailService} still degrades gracefully when no
 * {@code JavaMailSender} is configured (no {@code spring.mail.host}).
 */
@AutoConfiguration
@ConditionalOnClass(MimeMessage.class)
@Import(EmailConfig.class)
public class EmailAutoConfiguration {

  /** Creates the auto-configuration. */
  public EmailAutoConfiguration() {}
}
