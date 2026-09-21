package io.github.edmaputra.edidp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entrypoint for the ed-idp Identity Provider and OAuth 2.1 Authorization Server.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
