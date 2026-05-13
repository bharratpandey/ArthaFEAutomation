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

        // Only print actual browser errors – suppress log/warning/verbose noise
        page.onConsoleMessage(msg -> {
            if ("error".equals(msg.type())) {
                DashboardManager.log("[BROWSER ERROR] " + msg.text());
            }
        });

        // Capture page errors
        page.onPageError(err -> {
            DashboardManager.log("[PAGE ERROR] " + err);
        });
    }

    @AfterEach
    void closeContextAndPage() {
        if (page != null) page.close();
        if (context != null) context.close();
    }
}