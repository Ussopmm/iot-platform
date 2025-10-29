package io.ussopmm.eventcollectorservice.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;

import java.net.InetSocketAddress;

@Configuration
public class CassandraConfig {

    @Value("${spring.cassandra.contact-points:localhost}")
    private String contactPoint;

    @Value("${spring.cassandra.port:9042}")
    private int port;

    @Value("${spring.cassandra.local-datacenter:datacenter1}")
    private String localDc;

    // NOTE: данный бин ждет, пока не отработает cql скрипт с миграцией
    @Bean
    @Profile("!test")
    @DependsOn("flyway")
    public CqlSession cqlSession() {
        return CqlSession.builder()
                .addContactPoint(new InetSocketAddress(contactPoint, port))
                .withLocalDatacenter(localDc)
                .withKeyspace(System.getenv("CASSANDRA_KEYSPACE"))
                .build();
    }

}
