package io.github.edmaputra.edidp.integration;

import io.github.edmaputra.edidp.Application;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModules;

@SpringBootTest
class ApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void verifyModularStructure() {
		ApplicationModules.of(Application.class).verify();
	}
}
