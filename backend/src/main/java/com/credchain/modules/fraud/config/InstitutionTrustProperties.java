package com.credchain.modules.fraud.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Daily trust analysis of colleges (app.fraud.trust.* in application.yaml). */
@Validated
@ConfigurationProperties(prefix = "app.fraud.trust")
public record InstitutionTrustProperties(

        /** Days are counted in this time zone (it also decides what "midnight to 6 a.m." means). */
        @NotBlank String timeZone,

        /** How many days of history the model learns "normal" from. */
        @Min(14) @Max(365) int historyDays,

        /** When the analysis runs (Spring cron: second minute hour day month weekday). */
        @NotBlank String cron
) {
}
