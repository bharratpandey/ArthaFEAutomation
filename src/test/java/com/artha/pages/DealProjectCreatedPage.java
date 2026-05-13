package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.regex.Pattern;

import com.artha.utils.DashboardManager;

/**
 * Stage: PROJECT CREATED
 *
 * Entry condition:
 * - CLOSED WON stage completed successfully
 */
public class DealProjectCreatedPage {

    private final Page page;

    public DealProjectCreatedPage(Page page) {
        this.page = page;
    }

    // =====================================================
    // Generic safe click helper (React-safe)
    // =====================================================
    private void safeClick(Locator loc, String name) {
        try {
            loc.scrollIntoViewIfNeeded();
            loc.click();
            DashboardManager.log("[PROJECT] Clicked: " + name);
        } catch (Exception e) {
            try {
                loc.evaluate("el => el.click()");
                DashboardManager.log("[PROJECT] JS Clicked: " + name);
            } catch (Exception ex) {
                DashboardManager.log("[PROJECT] FAILED clicking " + name + " : " + ex.getMessage());
                throw ex;
            }
        }
    }

    // =====================================================
    // MAIN FLOW
    // =====================================================
    public boolean moveDealToProjectCreated(
            String deliverySpocName, // e.g. "Bharat"
            String notes             // notes for project creation
    ) {

        try {
            DashboardManager.log("[PROJECT] Starting Project Created flow...");

            // =====================================================
            // 1) Click PROJECT CREATED CTA
            // =====================================================
            Locator projectCreatedCta = page.locator("div")
                    .filter(new Locator.FilterOptions()
                            .setHasText(Pattern.compile("^\\s*PROJECT CREATED", Pattern.CASE_INSENSITIVE)))
                    .first();

            projectCreatedCta.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(8000));

            safeClick(projectCreatedCta, "Project Created CTA");

            // =====================================================
            // 2) Delivery SPOC (autocomplete)
            // =====================================================
            Locator deliverySpocInput = page.locator("input#delivery_spoc_id").first();
            deliverySpocInput.fill(deliverySpocName);
            page.waitForTimeout(500);

            Locator deliverySpocOption = page.getByRole(
                    AriaRole.OPTION,
                    new Page.GetByRoleOptions()
                            .setName(Pattern.compile(deliverySpocName, Pattern.CASE_INSENSITIVE))
            ).first();

            safeClick(deliverySpocOption, "Delivery SPOC: " + deliverySpocName);

            // =====================================================
            // 3) Notes
            // =====================================================
            Locator notesInput = page.locator("input[name='notes']").first();
            notesInput.fill(notes);

            // =====================================================
            // 4) Move To Project (Submit)
            // =====================================================
            Locator submitBtn = page.getByRole(
                    AriaRole.BUTTON,
                    new Page.GetByRoleOptions()
                            .setName(Pattern.compile("move to project", Pattern.CASE_INSENSITIVE))
            ).first();

            safeClick(submitBtn, "Move To Project");

            // =====================================================
            // 5) WAIT FOR SUCCESS TOAST (MANDATORY)
            // =====================================================
            DashboardManager.log("[PROJECT] Waiting for project creation success toast...");

            Locator successToast = page.locator("div")
                    .filter(new Locator.FilterOptions()
                            .setHasText("Deal has been converted into a project successfully"))
                    .first();

            successToast.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(30000));

            DashboardManager.log("[PROJECT] Success toast detected → Project created successfully");
            return true;

        } catch (Exception e) {
            DashboardManager.log("[PROJECT] FAILED to move deal to PROJECT CREATED: " + e.getMessage());
            return false;
        }
    }
}
