package com.gemmap.spot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Spot Service Application
 *
 * Handles spot/place management, image uploads, and geospatial operations
 */
@SpringBootApplication(scanBasePackages = {
    "com.gemmap.spot",
    "com.gemmap.common"
})
@EntityScan(basePackages = "com.gemmap.spot.image.domain.entity")
@EnableJpaRepositories(basePackages = "com.gemmap.spot.image.domain.repository")
public class SpotApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpotApplication.class, args);
	}
}
