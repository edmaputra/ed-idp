package io.github.edmaputra.edidp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Main entrypoint for the ed-idp Identity Provider and OAuth 2.1 Authorization Server.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@SpringBootApplication
@EntityScan(basePackages = "io.github.edmaputra.edidp")
@EnableJpaRepositories(basePackages = "io.github.edmaputra.edidp")
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
