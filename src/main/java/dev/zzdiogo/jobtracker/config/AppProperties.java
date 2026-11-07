package dev.zzdiogo.jobtracker.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Settings under the {@code app} prefix. The app refuses to start if they're invalid. */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotBlank String jwtSecret,
        @NotNull Duration jwtTtl,
        boolean registrationEnabled,
        @NotBlank String mailFrom) {}
