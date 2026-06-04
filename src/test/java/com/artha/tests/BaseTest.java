package com.artha.tests;

import com.artha.utils.DashboardManager;
import com.artha.utils.EmailSender;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

public class BaseTest {
    protected static Playwright playwright;
    protected static Browser browser;
    protected BrowserContext context;
    protected Page page;

    @BeforeAll
    static void globalSetup() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
        );
        DashboardManager.initReport();
        DashboardManager.startTest("Artha Deal E2E Flow");
    }

    @AfterAll
    static void globalTeardown() {
        DashboardManager.flushReport();
        EmailSender.sendDashboardEmail("bharat.pandey@emb.global,saumya.gupta@gmail.com");
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void createContextAndPage() {
        context = browser.newContext();
        page = context.newPage();

        // ── Browser console errors ────────────────────────────────────────
        // Drop known app-level noise (React background XHRs, menu hydration,
        // etc.). These never indicate a test failure — the test passes despite
        // them every run. Only surface truly unexpected console errors.
        page.onConsoleMessage(msg -> {
            if (!"error".equals(msg.type())) return;

            String text = msg.text();

            // Silently ignore common app-level noise
            if (text.contains("NetworkError")
                    || text.contains("Failed to load resource")
                    || text.contains("Invalid menu data")
                    || text.contains("Non-Error promise rejection")
                    || text.contains("ResizeObserver loop")
                    || text.contains("404")
                    || text.contains("500")) {
                return;
            }

            // Everything else is worth knowing about
            DashboardManager.log("[BROWSER ERROR] " + text);
        });

        // ── Page-level errors (uncaught exceptions) ───────────────────────
        // NetworkError / Failed to fetch are React SPA background blips —
        // not test failures. Log anything else.
        page.onPageError(err -> {
            String text = err == null ? "" : err.toString();

            if (text.contains("NetworkError")
                    || text.contains("Failed to fetch")
                    || text.contains("Load failed")) {
                return;
            }

            DashboardManager.log("[PAGE ERROR] " + text);
        });
    }

    @AfterEach
    void closeContextAndPage() {
        if (page != null) page.close();
        if (context != null) context.close();
    }
}