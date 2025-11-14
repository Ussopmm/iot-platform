package io.ussopmm.device_collector_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class DeviceCollectorServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DeviceCollectorServiceApplication.class, args);
	}

}
