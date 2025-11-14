package io.ussopmm.device_collector_service.config;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;


@Configuration
@Profile("!test & !kafka-it & !full-it")
public class FlywayMultiMigrationConfig {

    @PostConstruct
    public void migrateAllShards() {
        String username = System.getenv("SHARD_DB_USERNAME");
        String password = System.getenv("SHARD_DB_PASSWORD");
        String[] urls = {
                System.getenv("SHARD_MASTER_0_JDBC_URL"),
                System.getenv("SHARD_REPLICA_0_JDBC_URL"),
                System.getenv("SHARD_MASTER_1_JDBC_URL"),
                System.getenv("SHARD_REPLICA_1_JDBC_URL")
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
