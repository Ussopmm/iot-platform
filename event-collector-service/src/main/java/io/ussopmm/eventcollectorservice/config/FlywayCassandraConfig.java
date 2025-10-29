package io.ussopmm.eventcollectorservice.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class FlywayCassandraConfig {

    // NOTE: так как конфиг в application.yml файле не работал, пришлось в ручную настраивать
    // конфигурацию flyway migrations
    @Bean(name = "flyway", initMethod = "migrate")
    public Flyway flyway() {
        return Flyway.configure()
                .dataSource(System.getenv("CASSANDRA_DATABASE_URL"),
                        System.getenv("CASSANDRA_USER"), System.getenv("CASSANDRA_PASSWORD"))
                .locations(System.getenv("FLYWAY_MIGRATION_CLASSPATH"))
                .sqlMigrationSuffixes(System.getenv("FLYWAY_MIGRATION_SUFFIXES"))
                .schemas(System.getenv("FLYWAY_MIGRATION_SCHEMA"))
                .defaultSchema(System.getenv("FLYWAY_MIGRATION_SCHEMA"))
                .createSchemas(true)
                .failOnMissingLocations(true)
                .load();
    }
}
