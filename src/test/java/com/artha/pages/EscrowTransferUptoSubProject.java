package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Paths;

import com.artha.utils.DashboardManager;

public class EscrowTransferUptoSubProject {

    private final Page page;

    public EscrowTransferUptoSubProject(Page page) {
        this.page = page;
    }

    private void safeClick(Locator loc, String name) {
        try {
            loc.scrollIntoViewIfNeeded();
            loc.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(25000));
            loc.click(new Locator.ClickOptions().setTimeout(20000));
            DashboardManager.log("[Escrow SubProject Flow] Clicked: " + name);
        } catch (Exception e) {
            DashboardManager.log("[Escrow SubProject Flow] Normal click failed for " + name + " - using dispatchEvent");
            loc.evaluate("el => el.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true, view: window}))");
            DashboardManager.log("[Escrow SubProject Flow] JS dispatchEvent Clicked: " + name);
        }
    }

    private void waitForToast(String expectedMessage) {
        try {
            Locator toast = page.locator("div[role='status']:has-text('" + expectedMessage + "')");
            toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30000));
            String actualText = toast.innerText().trim();
            DashboardManager.log("[Escrow SubProject Flow] Toast DETECTED: '" + actualText + "' (expected: '" + expectedMessage + "')");
            toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(15000));
            DashboardManager.log("[Escrow SubProject Flow] Toast verified & hidden: " + expectedMessage);
        } catch (Exception e) {
            DashboardManager.log("[Escrow SubProject Flow] Toast '" + expectedMessage + "' not detected after 30s - continuing");
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get("escrow-sub-toast-missing-" + System.currentTimeMillis() + ".png"))
                    .setFullPage(true));
        }
    }

    /**
     * Transfers escrow from project → sub-project level.
     * @param projectId The project ID/name we just transferred escrow to (e.g. "AutoProj-...")
     * @return The same projectId (for chaining or logging)
     */
    public String transferEscrowUptoSubProject(String projectId) {
        try {
            DashboardManager.log("[Escrow SubProject Flow] Starting transfer from Project to Sub-Project...");

            // === STEP 1: GO TO PROJECT MODULE ===
            Locator projectModule = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='/projects'])").first();
            if (projectModule.count() == 0) {
                projectModule = page.locator("a[href*='/projects']:has-text('project')").first();
            }
            safeClick(projectModule, "Project Module");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            // === STEP 2: OPEN THE PROJECT WE JUST TRANSFERRED ESCROW TO ===
            Locator openProjectBtn = page.locator(
                    "xpath=//td/p[normalize-space(.)='" + projectId + "']//ancestor::tr//button[contains(text(), 'Open Project')]"
            ).first();

            if (openProjectBtn.count() == 0) {
                DashboardManager.log("[Escrow SubProject Flow] Could not find Open Project button for: " + projectId);
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("open-project-missing-" + System.currentTimeMillis() + ".png"))
                        .setFullPage(true));
                return projectId;
            }

            openProjectBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            safeClick(openProjectBtn, "Open Project: " + projectId);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(12000);

            // === STEP 3: RAISE TRANSFER REQUEST (PROJECT → SUB-PROJECT) ===
            Locator transferRequestBtn = page.locator("button:has-text('transfer request')").first();
            transferRequestBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(25000));
            safeClick(transferRequestBtn, "Transfer Request CTA (project → sub-project)");

            page.waitForTimeout(5000); // Modal load

            // === SELECT SUB-PROJECT RADIO BUTTON ===
            Locator subProjectRadio = page.locator("input[type='radio'][value='subproject'][name*=':r']").first();
            safeClick(subProjectRadio, "Sub Project Radio Button");

            // === SUB-PROJECT DROPDOWN (select twice because it fails first time) ===
            Locator subProjectBtn = page.locator("button[name='sub_project']").first();
            safeClick(subProjectBtn, "Sub Project Dropdown (first attempt)");

            page.waitForTimeout(2000);

            // First time: click again + ArrowDown + Enter
            safeClick(subProjectBtn, "Sub Project Dropdown (retry 1)");
            page.waitForTimeout(1000);
            page.keyboard().press("ArrowDown");
            page.waitForTimeout(500);
            page.keyboard().press("Enter");
            page.waitForTimeout(2000); // Wait for first selection attempt

            // Second time: click again + ArrowDown + Enter (final selection)
            safeClick(subProjectBtn, "Sub Project Dropdown (retry 2)");
            page.waitForTimeout(1000);
            page.keyboard().press("ArrowDown");
            page.waitForTimeout(500);
            page.keyboard().press("Enter");
            page.waitForTimeout(2000); // Longer wait for final selection to apply

            // Third time: click again + ArrowDown + Enter (final selection)
            safeClick(subProjectBtn, "Sub Project Dropdown (retry 3)");
            page.waitForTimeout(1000);
            page.keyboard().press("ArrowDown");
            page.waitForTimeout(500);
            page.keyboard().press("Enter");
            page.waitForTimeout(2000); // Longer wait for final selection to apply

            DashboardManager.log("[Escrow SubProject Flow] Sub-Project selected after double attempt");

            // === PI DROPDOWN - Select one PI (double ArrowDown to skip "Select All") ===
            Locator piInput = page.locator("input#PI").first();
            safeClick(piInput, "PI Dropdown (project → sub-project)");

            page.waitForTimeout(3000);

            // Double ArrowDown → skips "Select All" and highlights first real PI
            page.keyboard().press("ArrowDown");
            page.waitForTimeout(500);
            page.keyboard().press("ArrowDown");
            page.waitForTimeout(500);

            // Enter → selects the first PI (second in list)
            page.keyboard().press("Enter");
            page.waitForTimeout(4000); // Critical wait for total update

            DashboardManager.log("[Escrow SubProject Flow] Selected one PI (skipped Select All)");

            // Note
            Locator noteField = page.locator("textarea[name='Note']").first();
            noteField.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            noteField.fill("This is automated Note");

            // Confirm
            Locator confirmBtn = page.locator("button:has-text('confirm')").first();
            confirmBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            safeClick(confirmBtn, "Confirm CTA (project → sub-project)");

            waitForToast("Escrow transfer request raised successfully..!!");

            // === APPROVALS: REQUESTS → TRANSFER APPROVAL (HOD) ===
            Locator requestTab = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='poapproval'])").first();
            safeClick(requestTab, "Requests Tab");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(10000);

            Locator transferApprovalTab = page.locator("a:has-text('Transfer Approval')").first();
            safeClick(transferApprovalTab, "Transfer Approval Tab");

            // Wait for table + approve icon
            boolean tableLoaded = false;
            for (int attempt = 1; attempt <= 8 && !tableLoaded; attempt++) {
                page.waitForTimeout(5000);
                Locator rows = page.locator("table tbody tr");
                if (rows.count() > 0) {
                    tableLoaded = true;
                    DashboardManager.log("[Escrow SubProject Flow] Transfer Approval table loaded after " + attempt + " attempts");
                }
            }

            Locator hodApproveIcon = page.locator("svg[data-testid='CheckCircleOutlineIcon']").first();
            if (hodApproveIcon.isVisible()) {
                safeClick(hodApproveIcon, "Approve CTA (HOD)");
                Locator reasonField = page.locator("textarea[name='Reason']");
                reasonField.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
                reasonField.fill("This is automated filled reason");
                Locator submitBtn = page.locator("button:has-text('submit')").first();
                safeClick(submitBtn, "Submit CTA (HOD)");
                waitForToast("Escrow transfer request approved successfully.");
            } else {
                DashboardManager.log("[Escrow SubProject Flow] No HOD approve icon - screenshot");
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("sub-hod-no-approve-" + System.currentTimeMillis() + ".png"))
                        .setFullPage(true));
            }

            // === FINANCE → ESCROW TRANSFER → APPROVE ===
            Locator financeTab = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='embfinance'])").first();
            safeClick(financeTab, "Finance Tab");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(10000);

            Locator escrowTab = page.locator("div:has-text('Escrow Transfer')").first();
            safeClick(escrowTab, "Escrow Transfer Tab");

            tableLoaded = false;
            for (int attempt = 1; attempt <= 8 && !tableLoaded; attempt++) {
                page.waitForTimeout(5000);
                Locator rows = page.locator("table tbody tr");
                if (rows.count() > 0) {
                    tableLoaded = true;
                    DashboardManager.log("[Escrow SubProject Flow] Escrow Transfer table loaded after " + attempt + " attempts");
                }
            }

            Locator financeApproveIcon = page.locator("svg[data-testid='CheckCircleOutlineIcon']").first();
            if (financeApproveIcon.isVisible()) {
                safeClick(financeApproveIcon, "Approve CTA (Finance)");
                Locator reasonField = page.locator("textarea[name='Reason']");
                reasonField.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
                reasonField.fill("This is automated reason");
                Locator submitBtn = page.locator("button:has-text('submit')").first();
                safeClick(submitBtn, "Submit CTA (Finance)");
                waitForToast("Escrow transfer request approved successfully.");
            } else {
                DashboardManager.log("[Escrow SubProject Flow] No Finance approve icon - screenshot");
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("sub-finance-no-approve-" + System.currentTimeMillis() + ".png"))
                        .setFullPage(true));
            }

            DashboardManager.log("[Escrow SubProject Flow] Full transfer to Sub-Project completed!");
            return projectId;

        } catch (Exception e) {
            DashboardManager.log("[Escrow SubProject Flow] Error: " + e.getMessage());
            e.printStackTrace();
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get("escrow-sub-fail-" + System.currentTimeMillis() + ".png"))
                    .setFullPage(true));
            return projectId;
        }
    }
}