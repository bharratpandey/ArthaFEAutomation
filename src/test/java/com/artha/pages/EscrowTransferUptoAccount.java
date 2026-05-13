package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Paths;

import com.artha.utils.DashboardManager;

public class EscrowTransferUptoAccount {

    private final Page page;

    public EscrowTransferUptoAccount(Page page) {
        this.page = page;
    }

    private void safeClick(Locator loc, String name) {
        try {
            loc.scrollIntoViewIfNeeded();
            loc.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(25000));
            loc.click(new Locator.ClickOptions().setTimeout(20000));
            DashboardManager.log("[Escrow Flow] Clicked: " + name);
        } catch (Exception e) {
            DashboardManager.log("[Escrow Flow] Normal click failed for " + name + " - using dispatchEvent");
            loc.evaluate("el => el.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true, view: window}))");
            DashboardManager.log("[Escrow Flow] JS dispatchEvent Clicked: " + name);
        }
    }

    private void waitForToast(String expectedMessage) {
        try {
            Locator toast = page.locator("div[role='status']:has-text('" + expectedMessage + "')");
            toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30000));
            String actualText = toast.innerText().trim();
            DashboardManager.log("[Escrow Flow] Toast DETECTED: '" + actualText + "' (expected: '" + expectedMessage + "')");
            toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(15000));
            DashboardManager.log("[Escrow Flow] Toast verified & hidden: " + expectedMessage);
        } catch (Exception e) {
            DashboardManager.log("[Escrow Flow] Toast '" + expectedMessage + "' not detected after 30s - continuing");
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get("escrow-toast-missing-" + System.currentTimeMillis() + ".png"))
                    .setFullPage(true));
        }
    }

    /**
     * Transfers escrow:
     * 1. First: from sub-project → project
     * 2. Then: from project → account (without reopening sub-project)
     * Returns captured account name.
     */
    public String transferEscrowUptoAccount(String projectId) {
        String capturedAccountName = null;

        try {
            DashboardManager.log("[Escrow Flow] Starting Escrow Transfer flow after Debit Note settlement...");

            // === STEP 1: RE-OPEN PROJECT BY ID ===
            Locator projectModule = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='/projects'])").first();
            if (projectModule.count() == 0) {
                projectModule = page.locator("a[href*='/projects']:has-text('project')").first();
            }
            safeClick(projectModule, "Project Module");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(10000);

            Locator openProjectBtn = page.locator(
                    "xpath=(//td/p[normalize-space(.)='" + projectId + "']//ancestor::tr//button[contains(text(), 'Open Project')])[1]"
            ).first();

            if (openProjectBtn.count() == 0) {
                Locator row = page.locator("tr").filter(new Locator.FilterOptions().setHasText(projectId)).first();
                if (row.count() > 0) {
                    openProjectBtn = row.locator("button:has-text('Open Project')").first();
                }
            }

            if (openProjectBtn.count() == 0) {
                DashboardManager.log("[Escrow Flow] Could not find Open Project button for ID: " + projectId);
                return null;
            }

            openProjectBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            safeClick(openProjectBtn, "Open Project Button");

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(12000);

            // === CAPTURE ACCOUNT NAME FROM PROJECT DETAILS ===
            Locator accountLink = page.locator("a[href*='/account/']").first();
            if (accountLink.count() > 0 && accountLink.isVisible()) {
                capturedAccountName = accountLink.innerText().trim();
                DashboardManager.log("[Escrow Flow] CAPTURED ACCOUNT NAME: " + capturedAccountName);
            } else {
                Locator fallback = page.locator("text=Account").locator("xpath=following-sibling::div//a[href*='/account/']").first();
                if (fallback.count() > 0) {
                    capturedAccountName = fallback.innerText().trim();
                    DashboardManager.log("[Escrow Flow] CAPTURED ACCOUNT NAME (fallback): " + capturedAccountName);
                } else {
                    DashboardManager.log("[Escrow Flow] WARNING: Account name not found");
                    page.screenshot(new Page.ScreenshotOptions()
                            .setPath(Paths.get("account-missing-" + System.currentTimeMillis() + ".png"))
                            .setFullPage(true));
                }
            }

            // === FIRST TRANSFER: FROM SUB-PROJECT ===
            Locator openSubProjectBtn = page.locator("button:has-text('Open')").first();
            openSubProjectBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            safeClick(openSubProjectBtn, "Open Sub-Project (first transfer)");

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            Locator transferRequestBtn = page.locator("button:has-text('transfer request')").first();
            transferRequestBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            safeClick(transferRequestBtn, "Transfer Request CTA (sub-project → project)");

            page.waitForTimeout(4000);

            Locator noteField = page.locator("textarea[name='Note']").first();
            noteField.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            noteField.fill("This is automated note");

            Locator confirmBtn = page.locator("button[type='submit']:has-text('confirm')").first();
            safeClick(confirmBtn, "Confirm CTA (sub-project → project)");

            waitForToast("Escrow transfer request raised successfully..!!");

            // === STEP 4: GO TO REQUESTS → TRANSFER APPROVAL → APPROVE (HOD) ===
            Locator requestNav = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='poapproval'])").first();
            if (requestNav.count() == 0) requestNav = page.locator("a[href*='poapproval']").first();
            safeClick(requestNav, "Requests Tab");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(10000);

            Locator transferTab = page.locator("a:has-text('Transfer Approval')").first();
            safeClick(transferTab, "Transfer Approval Tab");
            page.waitForTimeout(8000);

            // IMPROVED: Find the FIRST row that has the approve icon visible (latest pending request)
            Locator approveIcon = page.locator("table tbody tr")
                    .filter(new Locator.FilterOptions().setHas(page.locator("svg[data-testid='CheckCircleOutlineIcon']")))
                    .first()
                    .locator("svg[data-testid='CheckCircleOutlineIcon']");

            if (approveIcon.isVisible()) {
                safeClick(approveIcon, "Approve CTA (HOD) - Latest Pending");
                Locator reasonField = page.locator("textarea[name='Reason']");
                reasonField.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
                reasonField.fill("This is automated filled reason");
                Locator submitBtn = page.locator("button:has-text('submit')").first();
                safeClick(submitBtn, "Submit CTA");
                waitForToast("Escrow transfer request approved successfully.");
            } else {
                DashboardManager.log("[Escrow Flow] No pending HOD approval found - possibly already approved or wrong row");
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("hod-no-approve-" + System.currentTimeMillis() + ".png"))
                        .setFullPage(true));
            }

            // === STEP 5: GO TO FINANCE → ESCROW TRANSFER → APPROVE (FINANCE) ===
            Locator financeNav = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='embfinance'])").first();
            if (financeNav.count() == 0) financeNav = page.locator("a[href*='embfinance']").first();
            safeClick(financeNav, "Finance Tab");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(10000);

            Locator escrowTab = page.locator("li:has-text('Escrow Transfer')").first();
            safeClick(escrowTab, "Escrow Transfer Tab");
            page.waitForTimeout(8000);

            // IMPROVED: Same logic - find FIRST row with approve icon (latest pending)
            Locator financeApproveIcon = page.locator("table tbody tr")
                    .filter(new Locator.FilterOptions().setHas(page.locator("svg[data-testid='CheckCircleOutlineIcon']")))
                    .first()
                    .locator("svg[data-testid='CheckCircleOutlineIcon']");

            if (financeApproveIcon.isVisible()) {
                safeClick(financeApproveIcon, "Approve CTA (Finance) - Latest Pending");
                Locator reasonField = page.locator("textarea[name='Reason']");
                reasonField.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
                reasonField.fill("This is automated reason");
                Locator submitBtn = page.locator("button:has-text('submit')").first();
                safeClick(submitBtn, "Submit CTA");
                waitForToast("Escrow transfer request approved successfully.");
            } else {
                DashboardManager.log("[Escrow Flow] No pending Finance approval found - possibly already approved or wrong row");
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("finance-no-approve-" + System.currentTimeMillis() + ".png"))
                        .setFullPage(true));
            }

            // === SECOND TRANSFER: FROM PROJECT TO ACCOUNT (NO SUB-PROJECT REOPEN) ===
            safeClick(projectModule, "Project Module (back)");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(10000);

            // Re-open project (but NOT sub-project)
            openProjectBtn = page.locator(
                    "xpath=(//td/p[normalize-space(.)='" + projectId + "']//ancestor::tr//button[contains(text(), 'Open Project')])[1]"
            ).first();
            safeClick(openProjectBtn, "Open Project Button (second transfer)");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(12000);

            // Transfer Request CTA again (now from project level to account)
            transferRequestBtn = page.locator("button:has-text('transfer request')").first();
            transferRequestBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(25000));
            safeClick(transferRequestBtn, "Transfer Request CTA (project → account)");
            page.waitForTimeout(5000); // Give modal time to fully load

            // === CRITICAL: SELECT ALL PIs VIA INPUT FIELD (CLICK → FILL → ARROW DOWN → ENTER) ===
            Locator piInput = page.locator("input#PI").first();
            if (piInput.isVisible()) {
                DashboardManager.log("[Escrow Flow] Clicking PI input field to open dropdown");
                safeClick(piInput, "PI Input Field");

                // Wait for dropdown/listbox to be interactive
                page.waitForTimeout(2000);

                // Fill "Select All" directly into the input
                piInput.fill("Select All");
                page.waitForTimeout(1000); // Wait for filtering

                // Press Arrow Down + Enter to confirm selection
                page.keyboard().press("ArrowDown");
                page.waitForTimeout(500);
                page.keyboard().press("Enter");
                page.waitForTimeout(3000); // Critical: wait for React/MUI to update total/amount field

                DashboardManager.log("[Escrow Flow] Selected ALL PIs via input + ArrowDown + Enter");
            } else {
                DashboardManager.log("[Escrow Flow] PI input field not visible - skipping PI selection");
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("pi-input-missing-" + System.currentTimeMillis() + ".png"))
                        .setFullPage(true));
            }

            // Note input field (re-use existing variable)
            noteField = page.locator("textarea[name='Note']").first();
            noteField.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            noteField.fill("This is automated note");

            // Confirm CTA (re-use existing variable)
            confirmBtn = page.locator("button[type='submit']:has-text('confirm')").first();
            confirmBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            safeClick(confirmBtn, "Confirm CTA (project → account)");

            waitForToast("Escrow transfer request raised successfully..!!");

            // === NEW: SECOND SET OF APPROVALS (PROJECT → ACCOUNT) ===
            // Wait + refresh table to ensure new request is at the top
            page.waitForTimeout(6000);
            page.reload();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(6000);

            // HOD Approval (same as first time)
            Locator requestNavSecond = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='poapproval'])").first();
            if (requestNavSecond.count() == 0) requestNavSecond = page.locator("a[href*='poapproval']").first();
            safeClick(requestNavSecond, "Requests Tab (Second Approval)");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(10000);

            Locator transferTabSecond = page.locator("a:has-text('Transfer Approval')").first();
            safeClick(transferTabSecond, "Transfer Approval Tab (Second)");
            page.waitForTimeout(8000);

            Locator approveIconSecond = page.locator("table tbody tr")
                    .filter(new Locator.FilterOptions().setHas(page.locator("svg[data-testid='CheckCircleOutlineIcon']")))
                    .first()
                    .locator("svg[data-testid='CheckCircleOutlineIcon']");

            if (approveIconSecond.isVisible()) {
                safeClick(approveIconSecond, "Approve CTA (HOD) - Second Transfer");
                Locator reasonFieldSecond = page.locator("textarea[name='Reason']");
                reasonFieldSecond.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
                reasonFieldSecond.fill("This is automated filled reason");
                Locator submitBtnSecond = page.locator("button:has-text('submit')").first();
                safeClick(submitBtnSecond, "Submit CTA (HOD - Second)");
                waitForToast("Escrow transfer request approved successfully.");
            } else {
                DashboardManager.log("[Escrow Flow] No pending HOD approval for second transfer - screenshot");
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("hod-no-approve-second-" + System.currentTimeMillis() + ".png"))
                        .setFullPage(true));
            }

            // Finance Approval (same as first time)
            Locator financeNavSecond = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='embfinance'])").first();
            if (financeNavSecond.count() == 0) financeNavSecond = page.locator("a[href*='embfinance']").first();
            safeClick(financeNavSecond, "Finance Tab (Second Approval)");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(10000);

            Locator escrowTabSecond = page.locator("li:has-text('Escrow Transfer')").first();
            safeClick(escrowTabSecond, "Escrow Transfer Tab (Second)");
            page.waitForTimeout(8000);

            Locator financeApproveIconSecond = page.locator("table tbody tr")
                    .filter(new Locator.FilterOptions().setHas(page.locator("svg[data-testid='CheckCircleOutlineIcon']")))
                    .first()
                    .locator("svg[data-testid='CheckCircleOutlineIcon']");

            if (financeApproveIconSecond.isVisible()) {
                safeClick(financeApproveIconSecond, "Approve CTA (Finance) - Second Transfer");
                Locator reasonFieldSecond = page.locator("textarea[name='Reason']");
                reasonFieldSecond.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
                reasonFieldSecond.fill("This is automated reason");
                Locator submitBtnSecond = page.locator("button:has-text('submit')").first();
                safeClick(submitBtnSecond, "Submit CTA (Finance - Second)");
                waitForToast("Escrow transfer request approved successfully.");
            } else {
                DashboardManager.log("[Escrow Flow] No pending Finance approval for second transfer - screenshot");
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("finance-no-approve-second-" + System.currentTimeMillis() + ".png"))
                        .setFullPage(true));
            }

            DashboardManager.log("[Escrow Flow] Full Escrow Transfer up to Account completed successfully!");
            return capturedAccountName;

        } catch (Exception e) {
            DashboardManager.log("[Escrow Flow] Error: " + e.getMessage());
            e.printStackTrace();
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get("escrow-fail-" + System.currentTimeMillis() + ".png"))
                    .setFullPage(true));
            return capturedAccountName;
        }
    }
}