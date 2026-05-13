package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.ArrayList;
import java.util.List;

import com.artha.utils.DashboardManager;

public class PMTPICreation {

    private final Page page;

    public PMTPICreation(Page page) {
        this.page = page;
    }

    private void safeClick(Locator loc, String name) {
        try {
            loc.scrollIntoViewIfNeeded();
            loc.click(new Locator.ClickOptions().setTimeout(15000));
            DashboardManager.log("[PI Flow] Clicked: " + name);
        } catch (Exception e) {
            DashboardManager.log("[PI Flow] Normal click failed for " + name + " - trying JS click");
            loc.evaluate("el => el.click()");
            DashboardManager.log("[PI Flow] JS Clicked: " + name);
        }
    }

    private void waitForToast(String expectedMessage) {
        try {
            Locator toast = page.locator("xpath=//div[@role='status']//*[contains(text(), '" + expectedMessage + "')]");
            toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            DashboardManager.log("[PI Flow] Toast verified: " + expectedMessage);
            toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(15000));
        } catch (Exception e) {
            DashboardManager.log("[PI Flow] Toast '" + expectedMessage + "' not detected - continuing");
        }
    }

    /**
     * Full PI Creation Flow for Project:
     * - Click PI tab
     * - Create 4 PIs (Mil1 to Mil4)
     * - Capture PI ID from second column (nth(1)) after creation
     * - Generate PI (push to Zoho)
     * - Navigate to Finance → Sales Orders → Generate CI → Mark as Paid for each PI
     */
    public boolean createPIAndCompleteFinanceFlow() {
        try {
            DashboardManager.log("[PI Flow] Starting full PI creation + Finance flow");

            // === CLICK PI TAB ===
            Locator piTab = page.locator("li[role='tab'][data-value='pi']").first();
            if (piTab.count() == 0) piTab = page.locator("li[role='tab']:has-text('PI')").first();
            if (piTab.count() == 0) piTab = page.locator("li.cursor-pointer:has(div:text('PI'))").first();

            piTab.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(25000));
            safeClick(piTab, "PI Tab");

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            List<String> piIds = new ArrayList<>();
            String[] milestones = {"Mil1", "Mil2", "Mil3"};

            for (String milestone : milestones) {
                DashboardManager.log("[PI Flow] Creating PI for milestone: " + milestone);

                // Click Create PI CTA
                Locator createPiBtn = page.locator("button:has-text('Create PI')").first();
                createPiBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
                safeClick(createPiBtn, "Create PI CTA");

                // Select Milestone
                Locator milestoneInput = page.locator("input#Milestone");
                milestoneInput.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
                milestoneInput.click();
                milestoneInput.fill(milestone);
                page.waitForTimeout(1000);
                page.keyboard().press("ArrowDown");
                page.keyboard().press("Enter");

                // Amount
                page.locator("input[name='PI_amount_exclusive_tax']").fill("100000");

                // Due Date (12/12/2025)
                Locator dueDate = page.locator("input[name='expected_shipment_date']");
                dueDate.pressSequentially("12122026", new Locator.PressSequentiallyOptions().setDelay(200));

                // Submit
                Locator submitBtn = page.locator("button[type='submit']:has-text('Create')").first();
                safeClick(submitBtn, "Create PI Submit");

                waitForToast("Sales Order has been created successfully");

                // === CAPTURE PI ID FROM SECOND COLUMN (nth(1)) ===
                Locator piTableRows = page.locator("div[data-value='pi'] table tbody tr");
                piTableRows.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));

                Locator latestRow = piTableRows.first();
                Locator piIdCell = latestRow.locator("td").nth(1).locator("span, p").first(); // nth(1) = second column
                String piId = piIdCell.innerText().trim();

                if (!piId.isEmpty() && piId.matches("\\d+")) {
                    DashboardManager.log("[PI Flow] CAPTURED PI ID: " + piId);
                    piIds.add(piId);
                } else {
                    DashboardManager.log("[PI Flow] Failed to capture valid PI ID (got: '" + piId + "')");
                    piIds.add("UNKNOWN_PI");
                }

                // Generate PI (push to Zoho)
                Locator generatePiIcon = latestRow.locator("svg[data-testid='SendTimeExtensionIcon']").first();
                if (generatePiIcon.isVisible()) {
                    safeClick(generatePiIcon, "Generate PI Icon");
                    waitForToast("Sales Order has been pushed into ZohoBooks");
                } else {
                    DashboardManager.log("[PI Flow] Generate PI icon not visible");
                }

                DashboardManager.log("[PI Flow] PI created and pushed for " + milestone);
            }

            DashboardManager.log("[PI Flow] All 4 PIs created with IDs: " + piIds);

            // === NAVIGATE TO FINANCE → SALES ORDERS ===
            Locator financeNav = page.locator("a[href*='embfinance']").first();
            if (financeNav.count() == 0) financeNav = page.locator("text=finance").first();
            safeClick(financeNav, "Finance Module");

            Locator salesOrderNav = page.locator("a[href*='pifinance']").first();
            if (salesOrderNav.count() == 0) salesOrderNav = page.locator("text=Sales Orders").first();
            safeClick(salesOrderNav, "Sales Orders Module");

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(12000); // Longer wait for PIs to appear in Finance

            // === PROCESS EACH PI IN FINANCE ===
            for (String piId : piIds) {
                if (piId.equals("UNKNOWN_PI")) {
                    DashboardManager.log("[Finance Flow] Skipping unknown PI ID");
                    continue;
                }

                DashboardManager.log("[Finance Flow] Processing CI + Payment for PI: " + piId);

                Locator targetRow = page.locator(
                        "xpath=//td[normalize-space(.)='" + piId + "']/ancestor::tr"
                ).first();

                if (targetRow.count() == 0) {
                    DashboardManager.log("[Finance Flow] Row not found for PI " + piId + " - reloading");
                    page.reload();
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    page.waitForTimeout(10000);
                    targetRow = page.locator(
                            "xpath=//td[normalize-space(.)='" + piId + "']/ancestor::tr"
                    ).first();
                }

                if (targetRow.count() == 0) {
                    DashboardManager.log("[Finance Flow] Still not found - skipping PI " + piId);
                    continue;
                }

                // Generate CI
                Locator generateCiIcon = targetRow.locator("svg[data-testid='ReceiptIcon']").first();
                if (generateCiIcon.isVisible()) {
                    safeClick(generateCiIcon, "Generate CI Icon");
                    Locator confirmBtn = page.locator("button:has-text('Confirm')").first();
                    confirmBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
                    safeClick(confirmBtn, "Confirm Generate CI");
                    waitForToast("Invoice has been created");
                }

                // Re-query row after CI
                targetRow = page.locator(
                        "xpath=//td[normalize-space(.)='" + piId + "']/ancestor::tr"
                ).first();

                // Mark as Paid
                Locator paidIcon = targetRow.locator("svg[data-testid='PaidIcon']").first();
                if (paidIcon.isVisible()) {
                    safeClick(paidIcon, "Mark as Paid Icon");

                    page.locator("input[name='transaction_id']")
                            .fill("CMS/CMS5438662773/MOOLCHAND HEALTHCARE PRIVATE L");

                    Locator paymentDate = page.locator("input[name='received_on']");
                    paymentDate.pressSequentially("12052025", new Locator.PressSequentiallyOptions().setDelay(200));

                    Locator updateBtn = page.locator("button:has-text('Update')").first();
                    safeClick(updateBtn, "Update Payment");

                    waitForToast("Payment has been created");
                }

                DashboardManager.log("[Finance Flow] Completed CI + Payment for PI: " + piId);
            }

            DashboardManager.log("[PI Flow] Full PI + Finance flow completed successfully!");
            return true;

        } catch (Exception e) {
            DashboardManager.log("[PI Flow] Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}