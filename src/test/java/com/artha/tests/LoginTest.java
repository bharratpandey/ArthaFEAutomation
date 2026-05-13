package com.artha.tests;

import com.artha.pages.LoginPage;
import com.microsoft.playwright.Page;             // <<-- required import
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.artha.utils.DashboardManager;

import static org.junit.jupiter.api.Assertions.*;

public class LoginTest extends BaseTest {

    private static final String DEV_URL = "https://dev-kam-v2.emb.global/login";
    private static final String TEST_EMAIL = "Bharat.pandey+1@emb.global";
    private static final String TEST_PASSWORD = "Emb@1234";

    @Test
    void loginTest() {
        LoginPage loginPage = new LoginPage(page);
        loginPage.navigateTo(DEV_URL);

        page.waitForTimeout(500);

        boolean success = false;

        try {
            success = loginPage.loginAndVerify(TEST_EMAIL, TEST_PASSWORD);

            if (!success) {
                saveDebugArtifacts("login-failure");
            }

            assertTrue(success, "Login failed — still on login page.");
        } catch (Exception e) {
            saveDebugArtifacts("login-exception");
            fail("Exception during login: " + e.getMessage());
        }
    }

    private void saveDebugArtifacts(String name) {
        try {
            Path dir = Paths.get("target", "debug", name);
            Files.createDirectories(dir);

            Path screenshot = dir.resolve("page.png");
            // Page.screenshot accepts a Path
            page.screenshot(new Page.ScreenshotOptions().setPath(screenshot));

            Path html = dir.resolve("page.html");
            Files.writeString(html, page.content());

            Path info = dir.resolve("info.txt");
            Files.writeString(info, "URL: " + page.url() + System.lineSeparator() + "Title: " + page.title());

            DashboardManager.log("\nDebug artifacts saved to: " + dir.toAbsolutePath());
        } catch (Exception ex) {
            System.err.println("Failed to save debug artifacts: " + ex.getMessage());
        }
    }
}
