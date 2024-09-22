package com.openelements.spring.services.keys;

import com.openelements.spring.services.tenant.TenantConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ComponentScan
@AutoConfiguration
@EnableAutoConfiguration
@Import(TenantConfig.class)
public class KeysConfig {
}
