package com.serdar.user.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Local/dev only — clears failed Flyway entries and realigns checksums before migrate.
 * Avoids manual DB repair after a migration typo (e.g. invalid VARCHAR length).
 */
@Configuration
@ConditionalOnProperty(name = "app.environment", havingValue = "dev")
public class DevFlywayRepairConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
