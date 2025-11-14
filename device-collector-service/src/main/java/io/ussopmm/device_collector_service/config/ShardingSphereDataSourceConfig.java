package io.ussopmm.device_collector_service.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.algorithm.core.config.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.mode.ModeConfiguration;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.readwritesplitting.config.ReadwriteSplittingRuleConfiguration;
import org.apache.shardingsphere.readwritesplitting.config.rule.ReadwriteSplittingDataSourceGroupRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.apache.shardingsphere.single.config.SingleRuleConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;

@Configuration
@Profile("!kafka-it")
public class ShardingSphereDataSourceConfig {

    @Value("${sharding.datasource.shard-master-0.jdbc-url:jdbc:postgresql://localhost:5434/devices?currentSchema=device}")
    private String shardMaster0JdbcUrl;

    @Value("${sharding.datasource.shard-replica-0.jdbc-url:jdbc:postgresql://localhost:5435/devices?currentSchema=device}")
    private String shardReplica0JdbcUrl;

    @Value("${sharding.datasource.shard-master-1.jdbc-url:jdbc:postgresql://localhost:5436/devices?currentSchema=device}")
    private String shardMaster1JdbcUrl;

    @Value("${sharding.datasource.shard-replica-1.jdbc-url:jdbc:postgresql://localhost:5437/devices?currentSchema=device}")
    private String shardReplica1JdbcUrl;

    @Value("${sharding.datasource.username:postgres}")
    private String shardUsername;

    @Value("${sharding.datasource.password:postgres}")
    private String shardPassword;



    @Bean
    @Primary
    public DataSource dataSource() throws SQLException {
        // Создаём физические DataSource'ы
        Map<String, DataSource> dataSourceMap = new HashMap<>();
        dataSourceMap.put("shard_master_0", createHikariDataSource(shardMaster0JdbcUrl, shardUsername, shardPassword));
        dataSourceMap.put("shard_replica_0", createHikariDataSource(shardReplica0JdbcUrl, shardUsername, shardPassword));
        dataSourceMap.put("shard_master_1", createHikariDataSource(shardMaster1JdbcUrl, shardUsername, shardPassword));
        dataSourceMap.put("shard_replica_1", createHikariDataSource(shardReplica1JdbcUrl, shardUsername, shardPassword));

        // Настраиваем правила Read-Write Splitting
        ReadwriteSplittingRuleConfiguration readWriteSplittingConfig = createReadWriteSplittingConfig();

        // Настраиваем правила Sharding
        ShardingRuleConfiguration shardingRuleConfig = createShardingConfig();

        SingleRuleConfiguration singleRuleConfiguration = createSingleRuleConfiguration();

        // Создаём ShardingSphere DataSource
        Collection<RuleConfiguration> ruleConfigurations = Arrays.asList(singleRuleConfiguration, readWriteSplittingConfig, shardingRuleConfig);

        // Mode configuration (standalone mode)
        ModeConfiguration modeConfig = null;

        Properties props = new Properties();
        props.setProperty("sql-show", "true");

        return ShardingSphereDataSourceFactory.createDataSource(null, modeConfig, dataSourceMap, ruleConfigurations, props);
    }

    private HikariDataSource createHikariDataSource(String jdbcUrl, String username, String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName("org.postgresql.Driver");
        return dataSource;
    }

    private ReadwriteSplittingRuleConfiguration createReadWriteSplittingConfig() {
        // Настройка shard0
        ReadwriteSplittingDataSourceGroupRuleConfiguration shard0Config = new ReadwriteSplittingDataSourceGroupRuleConfiguration(
                "shard0",
                "shard_master_0",
                Collections.singletonList("shard_replica_0"),
                "roundRobin"
        );

        // Настройка shard1
        ReadwriteSplittingDataSourceGroupRuleConfiguration shard1Config = new ReadwriteSplittingDataSourceGroupRuleConfiguration(
                "shard1",
                "shard_master_1",
                Collections.singletonList("shard_replica_1"),
                "roundRobin"
        );

        // Load balancer
        Map<String, AlgorithmConfiguration> loadBalancers = new HashMap<>();
        loadBalancers.put("roundRobin", new AlgorithmConfiguration("ROUND_ROBIN", new Properties()));

        return new ReadwriteSplittingRuleConfiguration(
                Arrays.asList(shard0Config, shard1Config),
                loadBalancers
        );
    }

    private ShardingRuleConfiguration createShardingConfig() {
        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();

        // Настройка таблицы devices
        // Используем логические имена из read-write splitting: shard0, shard1
        ShardingTableRuleConfiguration devicesTableRule = new ShardingTableRuleConfiguration(
                "devices",
                "shard${0..1}.devices"  // формат: datasource.schema.table
        );

        // Database sharding strategy
        Properties shardingProps = new Properties();
        shardingProps.setProperty("algorithm-expression", "shard${Math.abs(device_id.hashCode()) % 2}");

        AlgorithmConfiguration databaseShardingAlgorithm = new AlgorithmConfiguration("INLINE", shardingProps);

        devicesTableRule.setDatabaseShardingStrategy(
                new StandardShardingStrategyConfiguration("device_id", "deviceid_hash_mod")
        );

        shardingRuleConfig.getTables().add(devicesTableRule);
        shardingRuleConfig.getShardingAlgorithms().put("deviceid_hash_mod", databaseShardingAlgorithm);

        return shardingRuleConfig;
    }

    private SingleRuleConfiguration createSingleRuleConfiguration() {
        SingleRuleConfiguration rule = new SingleRuleConfiguration();
        rule.setTables(Collections.singletonList("*.device.*"));
        rule.setDefaultDataSource("shard_master_0");
        return rule;
    }



}

