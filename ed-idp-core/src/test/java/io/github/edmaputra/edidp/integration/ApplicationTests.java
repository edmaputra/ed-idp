package io.github.edmaputra.edidp.integration;

import io.github.edmaputra.edidp.Application;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModules;

import io.github.edmaputra.iam.adapter.rest.MfaOperationsDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(context.getBean(MfaOperationsDelegate.class)).isNotNull();
	}

	@Test
	void verifyModularStructure() {
		ApplicationModules.of(Application.class).verify();
	}
}
