package com.openelements.spring.base.services.tag;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring auto-configuration for the tag feature.
 *
 * <p>Component-scans this package so that {@link TagDataService} and {@link TagRepository} are
 * picked up. Imported by {@link com.openelements.spring.base.FullSpringServiceConfig} as part of
 * the full platform setup.
 */
@Configuration
@ComponentScan
@AutoConfiguration
@EnableAutoConfiguration
public class TagConfig {}
