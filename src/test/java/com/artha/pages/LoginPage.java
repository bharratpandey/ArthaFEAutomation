package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.Map;
import com.artha.utils.DashboardManager;

public class LoginPage {
    private final Page page;

    private final String emailSelector = "input[name='email']";
    private final String passwordSelector = "input[name='password']";
    private final String submitSelector = "button[type='submit']";
    // fallback form selector - try to locate a parent form if available
    private final String formSelector = "form";

    public LoginPage(Page page) {
        this.page = page;
    }

    public void navigateTo(String url) {
        page.navigate(url);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    }

    private void safeType(String selector, String value) {
        Locator loc = page.locator(selector);
        loc.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        loc.scrollIntoViewIfNeeded();

        // Try normal typing
        try {
            loc.click();
            loc.fill("");
            loc.type(value, new Locator.TypeOptions().setDelay(20));
            page.waitForTimeout(150);
            if (!loc.inputValue().equals(value)) {
                throw new RuntimeException("typed value not reflected");
            }
            return;
        } catch (Exception ignored) {}

        // Fallback 1: set value via DOM and dispatch input/change events
        try {
            String script =
                    "([sel, v]) => { " +
                            "  const el = document.querySelector(sel); " +
                            "  if (!el) return false; " +
                            "  el.focus(); el.value = v; " +
                            "  el.dispatchEvent(new Event('input', {bubbles:true})); " +
                            "  el.dispatchEvent(new Event('change', {bubbles:true})); " +
                            "  return el.value === v; " +
                            "}";
            boolean ok = (Boolean) page.evaluate(script, new Object[]{selector, value});
            page.waitForTimeout(150);
            if (ok) return;
        } catch (Exception ignored) {}

        // Fallback 2: set value via direct property
        try {
            page.evaluate("([sel,v]) => { const el = document.querySelector(sel); if (el) el.value = v; }",
                    new Object[]{selector, value});
            page.waitForTimeout(150);
        } catch (Exception ignored) {}
    }

    public void enterEmail(String emailValue) {
        safeType(emailSelector, emailValue);
    }

    public void enterPassword(String passwordValue) {
        safeType(passwordSelector, passwordValue);
    }

    public void clickSignIn() {
        Locator signIn = page.locator(submitSelector);
        // wait but allow fallback if button isn't found
        if (signIn.count() > 0) {
            signIn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            signIn.scrollIntoViewIfNeeded();
            try {
                signIn.click();
            } catch (Exception e) {
                signIn.click(new Locator.ClickOptions().setForce(true));
            }
        } else {
            // nothing to click — will try Enter or form submit in login flow
        }
    }

    /**
     * Attempts login with multiple fallbacks, then returns true if URL changed
     * or a common dashboard indicator appears.
     */
    public boolean loginAndVerify(String email, String password) {
        // attempt typed flow
        enterEmail(email);
        enterPassword(password);
        clickSignIn();

        // Wait shortly for navigation or network activity
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
        } catch (Exception ignored) {}

        // If still on /login do more attempts:
        if (page.url().contains("/login")) {
            // Try pressing Enter in password input
            try {
                page.locator(passwordSelector).press("Enter");
                page.waitForTimeout(1000);
            } catch (Exception ignored) {}
        }

        // last resort: submit the form via DOM
        if (page.url().contains("/login")) {
            try {
                page.evaluate("sel => { const f = document.querySelector(sel); if (f && typeof f.submit === 'function') { f.submit(); return true } return false }",
                        formSelector);
                page.waitForTimeout(1200);
            } catch (Exception ignored) {}
        }

        // allow load to settle
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(6000));
        } catch (Exception ignored) {}

        // Heuristic checks:
        boolean urlChanged = !page.url().contains("/login");
        boolean maybeDashboard = page.locator("text=Dashboard").count() > 0
                || page.locator("text=Logout").count() > 0
                || page.locator("[data-test-id='logout']").count() > 0;

        return urlChanged || maybeDashboard;
    }
}
