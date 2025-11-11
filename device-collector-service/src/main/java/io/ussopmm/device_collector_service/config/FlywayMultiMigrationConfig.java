package io.ussopmm.device_collector_service.config;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;


@Configuration
@Profile("!test & !kafka-it")
public class FlywayMultiMigrationConfig {

    @PostConstruct
    public void migrateAllShards() {
        String username = System.getenv("POSTGRES_USERNAME");
        String password = System.getenv("POSTGRES_PASSWORD");
        String[] urls = {
                System.getenv("POSTGRES_SHARD_URL_0"),
                System.getenv("POSTGRES_SHARD_URL_1"),
                System.getenv("POSTGRES_SHARD_URL_2"),
                System.getenv("POSTGRES_SHARD_URL_3")
        };

        for (String url : urls) {
            Flyway.configure()
                    .dataSource(url, username, password)
                    .locations("classpath:db/migration/v1")
                    .baselineVersion("1")
                    .baselineOnMigrate(true)
                    .validateOnMigrate(true)
                    .schemas("device", "device_history")
                    .defaultSchema("device")
                    .table("flyway_schema_history") // explicit, for clarity
                    .load()
                    .migrate();
        }
    }
}
