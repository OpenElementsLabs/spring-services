package com.openelements.spring.base.services.translation;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = LanguageConfig.class)
public class LanguageConfig {}
