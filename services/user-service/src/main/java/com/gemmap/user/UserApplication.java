package com.gemmap.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * User Service Application
 *
 * Handles authentication, authorization, and user management
 */
@SpringBootApplication(scanBasePackages = {
    "com.gemmap.user",
    "com.gemmap.common"
})
@EntityScan(basePackages = "com.gemmap.user.auth.domain.entity")
@EnableJpaRepositories(basePackages = "com.gemmap.user.auth.domain.repository")
public class UserApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserApplication.class, args);
	}
}
