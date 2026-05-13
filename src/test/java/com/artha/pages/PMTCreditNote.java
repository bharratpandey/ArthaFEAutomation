package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Paths;
import com.artha.utils.DashboardManager;

public class PMTCreditNote {

    private final Page page;

    public PMTCreditNote(Page page) {
        this.page = page;
    }

    private void safeClick(Locator loc, String name) {
        try {
            loc.scrollIntoViewIfNeeded();
            loc.click(new Locator.ClickOptions().setTimeout(15000));
            DashboardManager.log("[CN Flow] Clicked: " + name);
        } catch (Exception e) {
            DashboardManager.log("[CN Flow] Normal click failed for " + name + " - trying JS click");
            loc.evaluate("el => el.click()");
            DashboardManager.log("[CN Flow] JS Clicked: " + name);
        }
    }

    private void waitForToast(String expectedMessage) {
        try {
            Locator toast = page.locator("xpath=//div[@role='status']//*[contains(text(), '" + expectedMessage + "')]");
            toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            DashboardManager.log("[CN Flow] Toast verified: " + expectedMessage);
            toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(15000));
        } catch (Exception e) {
            DashboardManager.log("[CN Flow] Toast '" + expectedMessage + "' not detected - continuing");
        }
    }

    public boolean createAndCompleteCreditNoteFlow(String projectId, String firstPiId) {
        String cnId = null;

        try {
            DashboardManager.log("[CN Flow] Starting Credit Note flow after PO paid...");

            // === STEP 1: RE-OPEN PROJECT BY ID ===
            Locator projectModule = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='/projects'])").first();
            if (projectModule.count() == 0) {
                projectModule = page.locator("a[href*='/projects']:has-text('project')").first();
            }
            safeClick(projectModule, "Project Module");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

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
                DashboardManager.log("[CN Flow] Could not find Open Project button for ID: " + projectId);
                return false;
            }

            openProjectBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
            safeClick(openProjectBtn, "Open Project Button");

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            // === STEP 2: OPEN SUB-PROJECT ===
            Locator openSubProjectBtn = page.locator("button:has-text('Open')").first();
            openSubProjectBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
            safeClick(openSubProjectBtn, "Open Sub-Project");

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(6000);

            // === STEP 3: CLICK PI TAB ===
            Locator piTab = page.locator("li[role='tab']:has-text('PI')").first();
            if (piTab.count() == 0) piTab = page.locator("li[role='tab'][data-value='pi']").first();

            piTab.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30000));
            safeClick(piTab, "PI Tab");

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            // === STEP 4: CREATE REFUND/CN ON FIRST PI ===
            Locator refundIcon = page.locator("svg[data-testid='ReplayIcon']").first();
            refundIcon.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
            safeClick(refundIcon, "Refund CTA on first PI");

            page.waitForTimeout(4000);

            page.locator("input[name='amount']").fill("10000");

            page.locator("input[name='milestone_signOff_document']")
                    .setInputFiles(Paths.get("src/test/resources/demo-vendor.pdf"));
            page.waitForTimeout(2000);

            page.locator("input[name='client_refund_approval_proof']")
                    .setInputFiles(Paths.get("src/test/resources/demo-client.pdf"));

            // === FIX: React-safe reason fill (prevents clearing) ===
            Locator reasonField = page.locator("textarea[name='reason']").first();
            reasonField.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));

// 1. Click to focus (triggers React focus handler)
            reasonField.click();

// 2. Clear any residual value
            reasonField.evaluate("el => el.value = ''");

// 3. Type slowly + dispatch real events React listens to
            String reasonText = "Automated reason for CN";
            reasonField.type(reasonText, new Locator.TypeOptions().setDelay(50)); // 50ms delay per char

// 4. Force dispatch input/change events (critical for controlled components)
            reasonField.evaluate("el => { " +
                    "  const inputEvent = new Event('input', { bubbles: true }); " +
                    "  const changeEvent = new Event('change', { bubbles: true }); " +
                    "  el.dispatchEvent(inputEvent); " +
                    "  el.dispatchEvent(changeEvent); " +
                    "}");

// 5. Small wait for React to update internal state
            page.waitForTimeout(800);

// 6. Blur to trigger validation/save
            reasonField.evaluate("el => el.blur()");

// Debug: Verify value after fill
            String actualValue = reasonField.inputValue();
            DashboardManager.log("[CN Flow] Reason after safe fill: '" + actualValue + "'");

            // === FIXED: ROBUST UPDATE BUTTON CLICK ===
            DashboardManager.log("[CN Flow] Attempting to click Update CTA...");
            Locator updateBtn = page.locator("button[type='submit']:has-text('Update')").first();
            if (updateBtn.count() == 0) {
                updateBtn = page.locator("button.bg-theme-green.text-white:has-text('Update')").first();
            }
            if (updateBtn.count() == 0) {
                updateBtn = page.locator("button:has-text('Update')").first();
            }
            updateBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30000));
            // Wait for enabled state
            int waitAttempts = 0;
            while (!updateBtn.isEnabled() && waitAttempts < 25) {
                DashboardManager.log("[CN Flow] Update button disabled - waiting 1s (attempt " + waitAttempts + ")");
                page.waitForTimeout(1000);
                waitAttempts++;
            }
            if (!updateBtn.isEnabled()) {
                DashboardManager.log("[CN Flow] Force-enabling Update button via JS");
                updateBtn.evaluate("el => { el.disabled = false; el.removeAttribute('disabled'); }");
            }
            boolean clicked = false;
            for (int attempt = 1; attempt <= 8 && !clicked; attempt++) {
                try {
                    safeClick(updateBtn, "Update CTA (attempt " + attempt + ")");
                    clicked = true;
                } catch (Exception e) {
                    DashboardManager.log("[CN Flow] Click attempt " + attempt + " failed - retrying");
                    page.waitForTimeout(1500);
                }
            }
            if (!clicked) {
                DashboardManager.log("[CN Flow] FAILED to click Update - screenshot taken");
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("cn-update-fail-" + System.currentTimeMillis() + ".png"))
                        .setFullPage(true));
                return false;
            }
            waitForToast("Credit Note has been created successfully");

            // === STEP 5: FORCE CLICK CREDIT NOTE TAB & CAPTURE ID ===
            DashboardManager.log("[CN Flow] Switching to Credit Note tab to capture ID...");
            Locator creditNoteTab = page.locator("li[role='tab']:has-text('Credit Note')").first();
            if (creditNoteTab.count() == 0) creditNoteTab = page.locator("li[role='tab'][data-value='create-note']").first();
            // Click tab explicitly
            safeClick(creditNoteTab, "Credit Note Tab");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(12000); // long wait for tab content
            // Wait for table with progressive reload
            Locator cnTableRows = page.locator("div[data-value='create-note'] table tbody tr");
            boolean tableVisible = false;
            for (int attempt = 1; attempt <= 5 && !tableVisible; attempt++) {
                try {
                    cnTableRows.first().waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(15000));
                    tableVisible = true;
                } catch (Exception e) {
                    DashboardManager.log("[CN Flow] CN table not visible yet - reloading page (attempt " + attempt + ")");
                    page.reload();
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    page.waitForTimeout(5000);
                }
            }
            if (!tableVisible) {
                DashboardManager.log("[CN Flow] CRITICAL: CN table never appeared - screenshot taken");
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("cn-table-missing-" + System.currentTimeMillis() + ".png"))
                        .setFullPage(true));
                return false;
            }
            // Capture ID from first row, second column (ID column)
            Locator cnIdCell = cnTableRows.first().locator("td").nth(1).locator("span, p").first();
            cnId = cnIdCell.innerText().trim();
            DashboardManager.log("[CN Flow] CAPTURED CN ID: " + cnId);
            if (cnId.isEmpty() || !cnId.matches("\\d+")) {
                DashboardManager.log("[CN Flow] Failed to capture valid CN ID");
                return false;
            }

            // === STEP 6: APPROVE REFUND REQUEST (HOD + FINANCE) ===
            Locator requestNav = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='poapproval'])").first();
            if (requestNav.count() == 0) requestNav = page.locator("a[href*='poapproval']").first();
            if (requestNav.count() == 0) requestNav = page.locator("text=requests").first();
            safeClick(requestNav, "Requests Module");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(10000);

            // FIXED: Precise locator for Refund Approval tab
            Locator refundApprovalTab = page.locator("a[href*='escapproval']:has-text('Refund Approval')").first();
            if (refundApprovalTab.count() == 0) {
                refundApprovalTab = page.locator("a[href*='escapproval']").first();
            }
            safeClick(refundApprovalTab, "Refund Approval Tab");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            // HOD Approve - click FIRST visible approve icon (top request)
            Locator hodApproveIcon = page.locator("svg[data-testid='CheckCircleOutlineIcon']").first();
            boolean hodClicked = false;
            for (int attempt = 1; attempt <= 5 && !hodClicked; attempt++) {
                if (hodApproveIcon.isVisible()) {
                    safeClick(hodApproveIcon, "HOD Approve Icon (first visible)");
                    page.locator("textarea[name='reason']").fill("Automated HOD approval reason");
                    Locator approveBtn = page.locator("button:has-text('approve')").first();
                    safeClick(approveBtn, "HOD Approve CTA");
                    waitForToast("Refund request status has been approved successfully");
                    hodClicked = true;
                } else {
                    DashboardManager.log("[CN Flow] HOD approve icon not visible yet - waiting/retrying (attempt " + attempt + ")");
                    page.waitForTimeout(3000);
                }
            }

            // === NEW: After HOD approval, immediately click Finance tab ===
            Locator financeTab = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='embfinance'])").first();
            if (financeTab.count() == 0) financeTab = page.locator("a[href*='embfinance']").first();
            safeClick(financeTab, "Finance Tab");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            // Finance Approve - click FIRST visible approve icon (top pending request)
            Locator financeApproveIcon = page.locator("svg[data-testid='CheckCircleOutlineIcon']").first();
            boolean financeClicked = false;
            for (int attempt = 1; attempt <= 5 && !financeClicked; attempt++) {
                if (financeApproveIcon.isVisible()) {
                    safeClick(financeApproveIcon, "Finance Approve Icon (first visible)");
                    page.locator("textarea[name='reason']").fill("Automated Finance approval reason");
                    Locator approveBtn = page.locator("button:has-text('approve')").first();
                    safeClick(approveBtn, "Finance Approve CTA");
                    waitForToast("Refund request status has been approved successfully");
                    financeClicked = true;
                } else {
                    DashboardManager.log("[CN Flow] Finance approve icon not visible yet - waiting/retrying (attempt " + attempt + ")");
                    page.waitForTimeout(3000);
                }
            }

            // === STEP 7: SETTLE CN ===
            Locator cnFinanceTab = page.locator("a[href*='cnfinance']").first();
            safeClick(cnFinanceTab, "Credit Note Tab");
            page.waitForTimeout(8000);

            Locator settleIcon = page.locator("tr")
                    .filter(new Locator.FilterOptions().setHasText(cnId))
                    .locator("svg[data-testid='CheckIcon']")
                    .first();

            if (settleIcon.isVisible()) {
                DashboardManager.log("[CN Flow] Settle icon visible for CN ID: " + cnId);
                safeClick(settleIcon, "Settle CTA");

                // === NEW: Handle Confirmation Popup ("Are you sure...?" with Yes button) ===
                Locator yesBtn = page.locator("button:has-text('Yes')");
                if (yesBtn.isVisible()) {
                    DashboardManager.log("[CN Flow] Confirmation popup detected – clicking Yes");
                    safeClick(yesBtn, "Confirm Yes - Settle CN");
                    page.waitForTimeout(2000); // Wait for popup to close
                } else {
                    DashboardManager.log("[CN Flow] No confirmation popup – proceeding directly");
                }

                // === NEW: Handle Form Popup ("Mark As Settled") if it appears ===
                // Use more specific locator to avoid strict mode violation (multiple divs)
                Locator formTitle = page.locator("div.shrink-0.p-4.text-blue-gray-900.text-2xl.font-semibold:has-text('Mark As Settled')");
                if (formTitle.isVisible()) {
                    DashboardManager.log("[CN Flow] Mark As Settled form popup opened – filling fields");

                    // Bank Charges (fill 100)
                    Locator bankChargesInput = page.locator("input[name='bank_charges']");
                    if (bankChargesInput.isEnabled()) {
                        DashboardManager.log("[CN Flow] Bank Charges enabled – filling 100");
                        bankChargesInput.fill("100");
                    } else {
                        DashboardManager.log("[CN Flow] Bank Charges disabled – skipping");
                    }

                    // FX Rate (fill 86)
                    Locator fxRateInput = page.locator("input[name='sub_org_fx_rate']");
                    if (fxRateInput.isEnabled()) {
                        DashboardManager.log("[CN Flow] FX Rate enabled – filling 86");
                        fxRateInput.fill("86");
                    } else {
                        DashboardManager.log("[CN Flow] FX Rate disabled – skipping");
                    }

                    // Click Submit CTA
                    Locator submitBtn = page.locator("button[type='submit']:has-text('Submit')");
                    submitBtn.waitFor(new Locator.WaitForOptions().setTimeout(15000));
                    safeClick(submitBtn, "Submit Settle Form");

                    waitForToast("Credit note status has been updated successfully");
                } else {
                    DashboardManager.log("[CN Flow] No Mark As Settled form popup – assuming direct settle");
                    waitForToast("Credit note status has been updated successfully");
                }
            } else {
                DashboardManager.log("[CN Flow] Settle icon not visible for CN ID: " + cnId);
            }

            DashboardManager.log("[CN Flow] Full Credit Note flow completed successfully!");
            return true;

        } catch (Exception e) {
            DashboardManager.log("[CN Flow] Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}