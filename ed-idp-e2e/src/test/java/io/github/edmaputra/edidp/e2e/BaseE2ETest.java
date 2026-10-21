package io.github.edmaputra.edidp.e2e;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import io.github.edmaputra.edidp.ui.UiApplication;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Base test harness for Playwright-driven end-to-end integration tests.
 * Boots the full Spring Boot application (Core + UI add-on) on a random port
 * and wires up the Playwright browser context lifecycle and diagnostic reporting.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@SpringBootTest(
    classes = UiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "edidp.ui.enabled=true"
    }
)
@ExtendWith(PlaywrightExtension.class)
public abstract class BaseE2ETest {

  @LocalServerPort
  protected int port;

  /**
   * Returns the current active Playwright page for this test method.
   *
   * @return active Playwright {@link Page}
   */
  protected Page page() {
    return PlaywrightExtension.getPage();
  }

  /**
   * Returns the current isolated browser context for this test method.
   *
   * @return active Playwright {@link BrowserContext}
   */
  protected BrowserContext context() {
    return PlaywrightExtension.getContext();
  }

  /**
   * Constructs the base URL for the running embedded server.
   *
   * @return server root URL (e.g. {@code http://localhost:12345})
   */
  protected String baseUrl() {
    return "http://localhost:" + port;
  }

  /**
   * Resolves a relative application path to a fully qualified URL on the running test server.
   *
   * @param path relative path (e.g. {@code /admin/login})
   * @return full URL
   */
  protected String url(String path) {
    String normalized = path.startsWith("/") ? path : "/" + path;
    return baseUrl() + normalized;
  }
}
