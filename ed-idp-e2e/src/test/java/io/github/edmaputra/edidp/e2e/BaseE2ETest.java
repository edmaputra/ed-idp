package io.github.edmaputra.edidp.e2e;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.junit5.ScreenShooterExtension;
import io.github.edmaputra.edidp.ui.UiApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Base test harness for Selenide-driven end-to-end integration tests.
 * Boots the full Spring Boot application (Core + UI add-on) on a random port
 * and wires up the Selenide browser lifecycle, automatic failure screenshots, and test isolation.
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
@ExtendWith(ScreenShooterExtension.class)
public abstract class BaseE2ETest {

  @LocalServerPort
  protected int port;

  @BeforeAll
  static void configureSelenide() {
    boolean headless = Boolean.parseBoolean(System.getProperty("selenide.headless", "true"));
    String browser = System.getProperty("selenide.browser", "chrome");

    Configuration.headless = headless;
    Configuration.browser = browser;
    Configuration.browserSize = "1920x1080";
    Configuration.timeout = 8000;
    Configuration.reportsFolder = "target/selenide-reports";
    Configuration.savePageSource = true;

    ChromeOptions options = new ChromeOptions();
    options.addArguments(
        "--no-sandbox",
        "--disable-dev-shm-usage",
        "--disable-gpu"
    );
    Configuration.browserCapabilities = options;
  }

  @BeforeEach
  void resetBrowserState() {
    if (com.codeborne.selenide.WebDriverRunner.hasWebDriverStarted()) {
      try {
        Selenide.clearBrowserCookies();
        Selenide.clearBrowserLocalStorage();
      } catch (Exception ignored) {
        Selenide.closeWebDriver();
      }
    }
  }

  @AfterAll
  static void tearDownDriver() {
    Selenide.closeWebDriver();
  }

  /**
   * Constructs the base URL for the running embedded server.
   *
   * @return base URL (e.g. {@code http://localhost:PORT})
   */
  protected String baseUrl() {
    return "http://localhost:" + port;
  }

  /**
   * Constructs an absolute URL pointing to the given application path.
   *
   * @param path relative path (must start with /)
   * @return absolute URL
   */
  protected String url(String path) {
    return baseUrl() + (path.startsWith("/") ? path : "/" + path);
  }
}
