package com.credchain.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled background jobs (e.g. the issuer wallet sync worker). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}