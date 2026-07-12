package com.openelements.spring.base.services.comment;

import com.openelements.spring.base.services.user.UserConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = CommentConfig.class)
@Import(UserConfig.class)
public class CommentConfig {}
