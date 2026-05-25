package com.openelements.spring.base.services.comment;

import com.openelements.spring.base.services.user.UserConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ComponentScan
@AutoConfiguration
@EnableAutoConfiguration
@Import(UserConfig.class)
public class CommentConfig {}
