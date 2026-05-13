package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import com.artha.utils.DashboardManager;

import java.util.regex.Pattern;

/**
 * Stage: CLOSED WON
 * Entry condition:
 * - Partner Shortlisted DONE
 * - KYC & Documentation APPROVED
 */
public class DealClosedWonPage {

    private final Page page;

    public DealClosedWonPage(Page page) {
        this.page = page;
    }

    // =====================================================
    // Generic safe click helper (React-safe)
    // =====================================================
    private void safeClick(Locator loc, String name) {
        try {
            loc.scrollIntoViewIfNeeded();
            loc.click();
            DashboardManager.log("[CLOSED-WON] Clicked: " + name);
        } catch (Exception e) {
            try {
                loc.evaluate("el => el.click()");
                DashboardManager.log("[CLOSED-WON] JS Clicked: " + name);
            } catch (Exception ex) {
                DashboardManager.log("[CLOSED-WON] FAILED clicking " + name + " : " + ex.getMessage());
                throw ex;
            }
        }
    }

    // =====================================================
    // MAIN FLOW
    // =====================================================
    public boolean moveDealToClosedWon(
            String dealName,
            String salesSpocName,       // e.g. "Bharat Singh"
            String vendorCreditPeriod,  // e.g. "0 Days"
            String notes                // notes for closed won
    ) {

        try {
            DashboardManager.log("[CLOSED-WON] Starting Closed Won flow for deal: " + dealName);

            // =====================================================
            // 1) Go to DEALS module
            // =====================================================
            Locator dealsNav = page.getByRole(
                    AriaRole.LINK,
                    new Page.GetByRoleOptions()
                            .setName(Pattern.compile("^deals$", Pattern.CASE_INSENSITIVE))
            ).first();

            safeClick(dealsNav, "Deals Navigation");

            // =====================================================
            // 2) Open the deal card
            // =====================================================
            Locator dealCard = page.locator("h3")
                    .filter(new Locator.FilterOptions().setHasText(dealName))
                    .first();

            dealCard.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10000));

            safeClick(dealCard, "Deal Card: " + dealName);

            // =====================================================
            // 3) Click CLOSED WON CTA
            // =====================================================
            Locator closedWonCta = page.locator("div")
                    .filter(new Locator.FilterOptions()
                            .setHasText(Pattern.compile("^\\s*CLOSED WON", Pattern.CASE_INSENSITIVE)))
                    .first();

            closedWonCta.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(8000));

            safeClick(closedWonCta, "Closed Won CTA");

            // =====================================================
            // 4) Sales SPOC (autocomplete)
            // =====================================================
            Locator salesSpocInput = page.locator("input#sales_spoc_id").first();
            salesSpocInput.fill(salesSpocName);
            page.waitForTimeout(500);

            Locator spocOption = page.getByRole(
                    AriaRole.OPTION,
                    new Page.GetByRoleOptions()
                            .setName(Pattern.compile(salesSpocName, Pattern.CASE_INSENSITIVE))
            ).first();

            safeClick(spocOption, "Sales SPOC: " + salesSpocName);

            // =====================================================
            // 5) Vendor Credit Period dropdown
            // =====================================================
            Locator creditDropdown = page.locator("button[name='vendor_credit_period']").first();
            safeClick(creditDropdown, "Vendor Credit Period Dropdown");

            Locator creditOption = page.getByRole(
                    AriaRole.OPTION,
                    new Page.GetByRoleOptions()
                            .setName(Pattern.compile(vendorCreditPeriod, Pattern.CASE_INSENSITIVE))
            ).first();

            safeClick(creditOption, "Vendor Credit Period: " + vendorCreditPeriod);

            // =====================================================
            // 6) Notes
            // =====================================================
            Locator notesInput = page.locator("input[name='notes']").first();
            notesInput.fill(notes);

            // =====================================================
            // 7) Move To Closed Won (Submit)
            // =====================================================
            Locator submitBtn = page.getByRole(
                    AriaRole.BUTTON,
                    new Page.GetByRoleOptions()
                            .setName(Pattern.compile("move to closed won", Pattern.CASE_INSENSITIVE))
            ).first();

            safeClick(submitBtn, "Move To Closed Won");

            // =====================================================
            // 8) WAIT FOR SUCCESS TOAST (MANDATORY)
            // =====================================================
            DashboardManager.log("[CLOSED-WON] Waiting for success toast...");

            Locator successToast = page.locator("div")
                    .filter(new Locator.FilterOptions()
                            .setHasText("The deal is now on closed won stage"))
                    .first();

            successToast.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(20000));

            DashboardManager.log("[CLOSED-WON] Success toast detected → Deal moved to CLOSED WON");
            return true;

        } catch (Exception e) {
            DashboardManager.log("[CLOSED-WON] FAILED to move deal to CLOSED WON: " + e.getMessage());
            return false;
        }
    }
}
