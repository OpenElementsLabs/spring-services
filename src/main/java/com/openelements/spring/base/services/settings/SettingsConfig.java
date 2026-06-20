package com.openelements.spring.base.services.settings;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = SettingsConfig.class)
public class SettingsConfig {}
