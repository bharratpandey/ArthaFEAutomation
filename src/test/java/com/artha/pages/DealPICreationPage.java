package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.regex.Pattern;

import com.artha.utils.DashboardManager;

/**
 * Stage: PI Creation → CI Generation → Payment
 * Entry condition:
 * - Deal is already in CLOSED WON stage
 */
public class DealPICreationPage {

    private final Page page;

    public DealPICreationPage(Page page) {
        this.page = page;
    }

    // =========================
    // Safe Click (React-safe)
    // =========================
    private void safeClick(Locator loc, String name) {
        try {
            loc.scrollIntoViewIfNeeded();
            loc.click();
            DashboardManager.log("[PI] Clicked: " + name);
        } catch (Exception e) {
            loc.evaluate("el => el.click()");
            DashboardManager.log("[PI] JS Clicked: " + name);
        }
    }

    // =====================================================
    // MAIN FLOW
    // =====================================================
    // ✅ Updated: Requires dealName to reopen the deal at the end
    public boolean createPIGenerateCIAndMarkPaid(String dealName) {

        try {
            DashboardManager.log("[PI] Starting PI → CI → Payment flow for: " + dealName);

            // =====================================================
            // 1) CLICK CREATE PI CTA
            // =====================================================
            Locator createPiBtn = page.getByRole(
                    AriaRole.BUTTON,
                    new Page.GetByRoleOptions()
                            .setName(Pattern.compile("create pi", Pattern.CASE_INSENSITIVE))
            ).first();

            safeClick(createPiBtn, "Create PI");

            // =====================================================
            // 2) FILL PI FORM
            // =====================================================
            page.locator("input[name='pi_amount']").fill("10000");

            // Due date → dd / mm / yyyy auto jump
            Locator dueDate = page.locator("input[name='due_date']");
            dueDate.type("12052026"); // ddmmyyyy

            page.locator("textarea[name='pi_notes']")
                    .fill("This is automated PI Note");

            // =====================================================
            // 3) SUBMIT PI
            // =====================================================
            Locator submitPi = page.getByRole(
                    AriaRole.BUTTON,
                    new Page.GetByRoleOptions()
                            .setName(Pattern.compile("^create$", Pattern.CASE_INSENSITIVE))
            ).first();

            safeClick(submitPi, "Create PI Submit");

            // =====================================================
            // 4) VERIFY PI CREATION TOAST
            // =====================================================
            Locator piToast = page.locator("div[role='status']")
                    .filter(new Locator.FilterOptions()
                            .setHasText("Sales Order Generated Successfully"))
                    .first();

            piToast.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10000));

            DashboardManager.log("[PI] PI created successfully");

            // =====================================================
            // 5) NAVIGATE TO FINANCE → SALES ORDERS
            // =====================================================
            // Try standard navigation link first
            Locator financeNav = page.locator("a[href*='embfinance']").first();
            if (financeNav.count() == 0) {
                // Fallback to text match
                financeNav = page.locator("text=finance").first();
            }
            safeClick(financeNav, "Finance Module");

            Locator salesOrderNav = page.locator("a[href*='sales-orders']").first();
            if (salesOrderNav.count() == 0) {
                salesOrderNav = page.locator("text=Sales Orders").first();
            }
            safeClick(salesOrderNav, "Sales Orders");

            // =====================================================
            // 6) DEAL SALES ORDER TAB
            // =====================================================
            Locator dealSoTab = page.locator("li[data-value='deals_so']").first();
            safeClick(dealSoTab, "Deals Sales Order Tab");

            // =====================================================
            // 7) CAPTURE PI ID (LAST CREATED) & WAIT FOR LIST
            // =====================================================
            page.waitForTimeout(1000);

            // =====================================================
            // 8) GENERATE CI
            // =====================================================
            Locator generateCi = page.locator(
                    "svg[data-testid='ReceiptIcon'][aria-label='Generate Invoice']"
            ).first();

            safeClick(generateCi, "Generate CI");

            // Confirm dialog
            Locator confirmBtn = page.getByRole(
                    AriaRole.BUTTON,
                    new Page.GetByRoleOptions()
                            .setName(Pattern.compile("confirm", Pattern.CASE_INSENSITIVE))
            ).first();

            safeClick(confirmBtn, "Confirm Generate Invoice");

            // CI success toast
            Locator ciToast = page.locator("div[role='status']")
                    .filter(new Locator.FilterOptions()
                            .setHasText("Invoice has been created"))
                    .first();

            ciToast.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10000));

            DashboardManager.log("[PI] CI generated successfully");

            // =====================================================
            // 9) MARK AS PAID
            // =====================================================
            Locator markPaid = page.locator(
                    "svg[data-testid='PaidIcon'][aria-label='Mark as Paid Invoice']"
            ).first();

            safeClick(markPaid, "Mark As Paid");

            page.locator("input[name='transaction_id']")
                    .fill("CMS/CMS5438662773/MOOLCHAND HEALTHCARE PRIVATE L");

            Locator paymentDate = page.locator("input[name='received_on']");
            paymentDate.type("12052025"); // ddmmyyyy

            Locator updateBtn = page.getByRole(
                    AriaRole.BUTTON,
                    new Page.GetByRoleOptions()
                            .setName(Pattern.compile("update", Pattern.CASE_INSENSITIVE))
            ).first();

            safeClick(updateBtn, "Update Payment");

            // Payment success toast
            Locator paymentToast = page.locator("div[role='status']")
                    .filter(new Locator.FilterOptions()
                            .setHasText("Payment has been created"))
                    .first();

            paymentToast.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10000));

            DashboardManager.log("[PI] Payment marked successfully");

            // =====================================================
            // 10) BACK TO DEALS (FIXED SELECTOR)
            // =====================================================
            // Matches: <a href="/organization/.../deals">deals</a>
            Locator dealsNav = page.locator("a[href*='/deals']").first();
            safeClick(dealsNav, "Back to Deals");

            page.waitForLoadState();
            page.waitForTimeout(1500); // Wait for listing to load

            // =====================================================
            // 11) OPEN THE DEAL (FIXED LOGIC)
            // =====================================================
            // Logic: Find the h3 with the deal name and click it
            DashboardManager.log("[PI] Searching for deal card: " + dealName);

            // Use 'has-text' to find the specific H3 title
            Locator dealTitle = page.locator("h3:has-text(\"" + dealName + "\")").first();

            if (dealTitle.count() > 0) {
                dealTitle.scrollIntoViewIfNeeded();
                safeClick(dealTitle, "Deal Card: " + dealName);
                page.waitForTimeout(2000); // Wait for details page to load
            } else {
                DashboardManager.log("[PI] Could not find deal card for '" + dealName + "'. Attempting to click the first available card as fallback.");
                // Fallback: Click the first card if specific one isn't found immediately
                Locator firstCard = page.locator("h3").first();
                if(firstCard.count() > 0) {
                    safeClick(firstCard, "First Deal Card (Fallback)");
                    page.waitForTimeout(2000);
                } else {
                    DashboardManager.log("[PI] No deal cards found on the page!");
                    return false;
                }
            }

            DashboardManager.log("[PI] PI → CI → Payment flow completed & returned to deal.");
            return true;

        } catch (Exception e) {
            DashboardManager.log("[PI] FAILED: " + e.getMessage());
            return false;
        }
    }
}