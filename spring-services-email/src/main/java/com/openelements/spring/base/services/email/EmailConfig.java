package com.openelements.spring.base.services.email;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Configuration for email sending beans. */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = EmailConfig.class)
public class EmailConfig {}
