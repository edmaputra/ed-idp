package io.github.edmaputra.edidp.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit 5 Extension managing the Playwright lifecycle, browser contexts,
 * and diagnostic report generation (traces, screenshots, and videos on test failure).
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class PlaywrightExtension implements
    BeforeAllCallback,
    AfterAllCallback,
    BeforeEachCallback,
    AfterEachCallback,
    AfterTestExecutionCallback {

  private static final Logger log = LoggerFactory.getLogger(PlaywrightExtension.class);

  private static final Path TRACES_DIR = Paths.get("target", "playwright-traces");
  private static final Path SCREENSHOTS_DIR = Paths.get("target", "playwright-screenshots");
  private static final Path VIDEOS_DIR = Paths.get("target", "playwright-videos");

  private static Playwright playwright;
  private static Browser browser;

  private static final ThreadLocal<BrowserContext> contextHolder = new ThreadLocal<>();
  private static final ThreadLocal<Page> pageHolder = new ThreadLocal<>();

  public static Page getPage() {
    return pageHolder.get();
  }

  public static BrowserContext getContext() {
    return contextHolder.get();
  }

  @Override
  public void beforeAll(ExtensionContext extensionContext) {
    if (playwright == null) {
      log.info("Initializing Playwright runtime and headless browser...");
      playwright = Playwright.create();

      boolean headless = Boolean.parseBoolean(System.getProperty("playwright.headless", "true"));
      double slowMo = Double.parseDouble(System.getProperty("playwright.slowmo", "0"));

      BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
          .setHeadless(headless)
          .setSlowMo(slowMo);

      String browserType = System.getProperty("playwright.browser", "chromium").toLowerCase();
      browser = switch (browserType) {
        case "firefox" -> playwright.firefox().launch(launchOptions);
        case "webkit" -> playwright.webkit().launch(launchOptions);
        default -> playwright.chromium().launch(launchOptions);
      };
      log.info("Playwright browser launched: {} (headless: {})", browserType, headless);
    }
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) throws IOException {
    Files.createDirectories(TRACES_DIR);
    Files.createDirectories(SCREENSHOTS_DIR);
    Files.createDirectories(VIDEOS_DIR);

    Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
        .setRecordVideoDir(VIDEOS_DIR)
        .setRecordVideoSize(1280, 720);

    BrowserContext context = browser.newContext(contextOptions);
    contextHolder.set(context);

    context.tracing().start(new Tracing.StartOptions()
        .setScreenshots(true)
        .setSnapshots(true)
        .setSources(true));

    Page page = context.newPage();
    pageHolder.set(page);
  }

  @Override
  public void afterTestExecution(ExtensionContext extensionContext) {
    Optional<Throwable> failure = extensionContext.getExecutionException();
    String testClassName = extensionContext.getRequiredTestClass().getSimpleName();
    String testMethodName = extensionContext.getRequiredTestMethod().getName();
    String identifier = testClassName + "_" + testMethodName;

    BrowserContext context = contextHolder.get();
    Page page = pageHolder.get();

    if (failure.isPresent()) {
      log.warn("Test failed: [{}]. Capturing Playwright diagnostic artifacts...", identifier);

      if (page != null) {
        Path screenshotPath = SCREENSHOTS_DIR.resolve(identifier + ".png");
        try {
          page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(true));
          log.info("Saved failure screenshot: {}", screenshotPath.toAbsolutePath());
        } catch (Exception ex) {
          log.error("Failed to capture screenshot for test: {}", identifier, ex);
        }
      }

      if (context != null) {
        Path tracePath = TRACES_DIR.resolve(identifier + ".zip");
        try {
          context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
          log.info("Saved Playwright trace: {}", tracePath.toAbsolutePath());
        } catch (Exception ex) {
          log.error("Failed to save trace for test: {}", identifier, ex);
        }
      }
    } else {
      if (context != null) {
        try {
          context.tracing().stop();
        } catch (Exception ignored) {
          // Tracing discard if test passed
        }
      }
    }
  }

  @Override
  public void afterEach(ExtensionContext extensionContext) {
    Page page = pageHolder.get();
    if (page != null) {
      Path videoPath = page.video() != null ? page.video().path() : null;
      try {
        page.close();
      } catch (Exception ignored) {
      } finally {
        pageHolder.remove();
      }

      // If test succeeded, remove the video recording to save disk space
      if (extensionContext.getExecutionException().isEmpty() && videoPath != null) {
        try {
          Files.deleteIfExists(videoPath);
        } catch (IOException ignored) {
        }
      }
    }

    BrowserContext context = contextHolder.get();
    if (context != null) {
      try {
        context.close();
      } catch (Exception ignored) {
      } finally {
        contextHolder.remove();
      }
    }
  }

  @Override
  public void afterAll(ExtensionContext extensionContext) {
    if (browser != null) {
      try {
        browser.close();
      } catch (Exception ignored) {
      } finally {
        browser = null;
      }
    }
    if (playwright != null) {
      try {
        playwright.close();
      } catch (Exception ignored) {
      } finally {
        playwright = null;
      }
    }
  }
}
