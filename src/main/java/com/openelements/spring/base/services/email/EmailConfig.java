package com.openelements.spring.base.services.email;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Configuration for email sending beans. */
@Configuration
@ComponentScan
@AutoConfiguration
@EnableAutoConfiguration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfig {}
