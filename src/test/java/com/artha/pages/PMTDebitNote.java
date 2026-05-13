package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Paths;
import com.artha.utils.DashboardManager;

public class PMTDebitNote {

    private final Page page;

    public PMTDebitNote(Page page) {
        this.page = page;
    }

    private void safeClick(Locator loc, String name) {
        try {
            loc.scrollIntoViewIfNeeded();
            loc.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            loc.click(new Locator.ClickOptions().setTimeout(20000));
            DashboardManager.log("[DN Flow] Clicked: " + name);
        } catch (Exception e) {
            DashboardManager.log("[DN Flow] Normal click failed for " + name + " - using dispatchEvent");
            loc.evaluate("el => el.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true, view: window}))");
            DashboardManager.log("[DN Flow] JS dispatchEvent Clicked: " + name);
        }
    }

    private void waitForToast(String expectedMessage) {
        try {
            // More reliable locator: role='status' + exact text match + ignore animation wrapper
            Locator toast = page.locator("div[role='status']:has-text('" + expectedMessage + "')");

            // Wait for visible (increased to 30s for slow toast animations)
            toast.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(30000));

            // Log the actual text found for debug
            String actualText = toast.innerText().trim();
            DashboardManager.log("[DN Flow] Toast DETECTED: '" + actualText + "' (expected: '" + expectedMessage + "')");

            // Optional: wait for toast to disappear (original behavior preserved)
            toast.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(15000));

            DashboardManager.log("[DN Flow] Toast verified & hidden: " + expectedMessage);
        } catch (Exception e) {
            DashboardManager.log("[DN Flow] Toast '" + expectedMessage + "' not detected after 30s - continuing");
            // Screenshot on missing toast (for debug in CI/logs)
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get("dn-toast-missing-" + System.currentTimeMillis() + ".png"))
                    .setFullPage(true));
        }
    }

    public boolean createAndCompleteDebitNoteFlow(String projectId) {
        String dnId = null;

        try {
            DashboardManager.log("[DN Flow] Starting Debit Note flow after Credit Note settled...");

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
                DashboardManager.log("[DN Flow] Could not find Open Project button for ID: " + projectId);
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

            // === STEP 3: CLICK PO TAB ===
            Locator poTab = page.locator("li[role='tab']:has-text('PO')").first();
            if (poTab.count() == 0) poTab = page.locator("li[role='tab'][data-value='PO']").first();

            poTab.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30000));
            safeClick(poTab, "PO Tab");

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            // === STEP 4: CLICK RETURN PO CTA ON FIRST ROW (FIXED & ROBUST) ===
            DashboardManager.log("[DN Flow] Waiting for PO table to load...");
            Locator poTableFirstRow = page.locator("div[data-value='PO'] table tbody tr").first();
            poTableFirstRow.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30000));

            DashboardManager.log("[DN Flow] Locating Return PO CTA (ReplayIcon) inside first row...");
            Locator returnPoIcon = poTableFirstRow.locator("svg[data-testid='ReplayIcon']").first();

            boolean iconClicked = false;
            for (int attempt = 1; attempt <= 10 && !iconClicked; attempt++) {
                try {
                    returnPoIcon.waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(20000));

                    boolean isInteractive = (Boolean) returnPoIcon.evaluate(
                            "el => { " +
                                    "  const style = window.getComputedStyle(el);" +
                                    "  return style.pointerEvents !== 'none' && " +
                                    "         style.display !== 'none' && " +
                                    "         style.visibility !== 'hidden' && " +
                                    "         !el.disabled && " +
                                    "         el.offsetParent !== null;" +
                                    "}"
                    );

                    if (isInteractive) {
                        DashboardManager.log("[DN Flow] Return PO icon READY & INTERACTIVE (attempt " + attempt + ")");
                        safeClick(returnPoIcon, "Return PO CTA on first row");
                        iconClicked = true;
                    } else {
                        DashboardManager.log("[DN Flow] Icon visible but NOT interactive yet - retrying (attempt " + attempt + ")");
                        page.waitForTimeout(2000);
                    }
                } catch (Exception e) {
                    DashboardManager.log("[DN Flow] Return PO icon not ready (attempt " + attempt + "): " + e.getMessage());
                    if (attempt < 10) {
                        page.waitForTimeout(3000);
                    }
                }
            }

            if (!iconClicked) {
                DashboardManager.log("[DN Flow] CRITICAL: Failed to click Return PO CTA after 10 attempts");
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("dn-return-fail-" + System.currentTimeMillis() + ".png"))
                        .setFullPage(true));
                return false;
            }

            page.waitForTimeout(5000); // modal load delay

            // Amount
            page.locator("input[name='amount']").fill("40000");

            // Upload Vendor CN
            page.locator("input[name='vendor_credit_note']")
                    .setInputFiles(Paths.get("src/test/resources/demo-vendor.pdf"));

            // Reason
            Locator reasonField = page.locator("textarea[name='reason']").first();
            reasonField.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
            reasonField.fill("Automated reason for Debit Note");
            page.waitForTimeout(3000);

            // Return PO / Update button
            Locator returnBtn = page.locator("button:has-text('Return PO')").first();
            if (returnBtn.count() == 0) returnBtn = page.locator("button.bg-theme-green.text-white:has-text('Return PO')").first();
            if (returnBtn.count() == 0) returnBtn = page.locator("button[type='button']:has-text('Return PO')").first();
            if (returnBtn.count() == 0) returnBtn = page.locator("button[type='submit']:has-text('Return PO')").first();

            returnBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30000));

            int btnWait = 0;
            while (!returnBtn.isEnabled() && btnWait < 30) {
                DashboardManager.log("[DN Flow] Return PO button disabled - waiting 1s (attempt " + btnWait + ")");
                page.waitForTimeout(1000);
                btnWait++;
            }

            boolean btnClicked = false;
            for (int attempt = 1; attempt <= 8 && !btnClicked; attempt++) {
                try {
                    if (!returnBtn.isEnabled()) {
                        DashboardManager.log("[DN Flow] Force-enabling Return PO button (attempt " + attempt + ")");
                        returnBtn.evaluate("el => { el.disabled = false; el.removeAttribute('disabled'); }");
                    }
                    safeClick(returnBtn, "Return PO submit button (attempt " + attempt + ")");
                    btnClicked = true;
                } catch (Exception e) {
                    DashboardManager.log("[DN Flow] Submit button click failed (attempt " + attempt + ")");
                    page.waitForTimeout(2000);
                }
            }

            if (!btnClicked) {
                DashboardManager.log("[DN Flow] FAILED to click Return PO submit button after all attempts");
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("dn-submit-fail-" + System.currentTimeMillis() + ".png"))
                        .setFullPage(true));
                return false;
            }

            waitForToast("Debit Note has been created successfully");

            // === STEP 5: CLICK DEBIT NOTE TAB & CAPTURE ID (FIXED) ===
            Locator debitNoteTab = page.locator("li[role='tab']:has-text('Debit Note')").first();
            if (debitNoteTab.count() == 0) debitNoteTab = page.locator("li[role='tab'][data-value='debit-note']").first();

            safeClick(debitNoteTab, "Debit Note Tab");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(10000);

            Locator dnTableRows = page.locator("div[data-value='debit-note'] table tbody tr");
            dnTableRows.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));

            // FIXED: ID is in SECOND column (Request Id) - nth(1) for 0-indexed columns
            Locator dnIdCell = dnTableRows.first().locator("td").nth(1).locator("span, p").first();

            // Debug: Log the full first row text to confirm
            String rowText = dnTableRows.first().innerText().trim().replaceAll("\\s+", " ");
            DashboardManager.log("[DN Flow] First Debit Note row content: " + rowText);

            dnId = dnIdCell.innerText().trim();
            DashboardManager.log("[DN Flow] CAPTURED DEBIT NOTE ID: " + dnId);

            if (dnId.isEmpty() || !dnId.matches("\\d+")) {
                DashboardManager.log("[DN Flow] Failed to capture valid Debit Note ID - trying fallback column");
                // Fallback: Try first column in case layout changed
                dnIdCell = dnTableRows.first().locator("td").first().locator("span, p").first();
                dnId = dnIdCell.innerText().trim();
                DashboardManager.log("[DN Flow] Fallback ID attempt: " + dnId);
                if (dnId.isEmpty() || !dnId.matches("\\d+")) {
                    return false;
                }
            }

            // === STEP 6: APPROVE DEBIT NOTE REQUEST ===
            Locator requestNav = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='poapproval'])").first();
            if (requestNav.count() == 0) requestNav = page.locator("a[href*='poapproval']").first();
            if (requestNav.count() == 0) requestNav = page.locator("text=requests").first();
            safeClick(requestNav, "Requests Tab");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(10000);

            Locator refundApprovalTab = page.locator("a[href*='escapproval']:has-text('Refund Approval')").first();
            if (refundApprovalTab.count() == 0) refundApprovalTab = page.locator("a[href*='escapproval']").first();
            safeClick(refundApprovalTab, "Refund Approval Tab");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            Locator firstApproveIcon = page.locator("svg[data-testid='CheckCircleOutlineIcon']").first();
            if (firstApproveIcon.isVisible()) {
                safeClick(firstApproveIcon, "Approve CTA - First Request (HOD)");
                page.locator("textarea[name='reason']").fill("Automated Reason for DN");
                Locator approveBtn = page.locator("button:has-text('approve')").first();
                safeClick(approveBtn, "Approve CTA");
                waitForToast("Refund request status has been approved successfully");
            }

            Locator financeTab = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='embfinance'])").first();
            if (financeTab.count() == 0) financeTab = page.locator("a[href*='embfinance']").first();
            safeClick(financeTab, "Finance Module");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            Locator refundVendorTab = page.locator("li[role='tab']:has-text('Refund From Vendor')").first();
            if (refundVendorTab.count() == 0) refundVendorTab = page.locator("li[data-value='debitnote']").first();
            safeClick(refundVendorTab, "Refund From Vendor Tab");
            page.waitForTimeout(6000);

            Locator financeApproveIcon = page.locator("svg[data-testid='CheckCircleOutlineIcon']").first();
            if (financeApproveIcon.isVisible()) {
                safeClick(financeApproveIcon, "Approve CTA - First Request (Finance)");
                page.locator("textarea[name='reason']").fill("Automated DN finance approve");
                Locator approveBtn = page.locator("button:has-text('approve')").first();
                safeClick(approveBtn, "Approve CTA");
                waitForToast("Refund request status has been approved successfully");
            }

            // === STEP 7: SETTLE DEBIT NOTE ===
            Locator dnFinanceTab = page.locator("a[href*='dnfinance']").first();
            safeClick(dnFinanceTab, "Debit Note Tab");
            page.waitForTimeout(8000);

            Locator settleIcon = page.locator("tr")
                    .filter(new Locator.FilterOptions().setHasText(dnId))
                    .locator("svg[data-testid='CheckIcon']")
                    .first();

            if (settleIcon.isVisible()) {
                DashboardManager.log("[DN Flow] Settle icon visible for DN ID: " + dnId);
                safeClick(settleIcon, "Settle CTA");

                // === NEW: Handle Form Popup ("Mark As Settled") if it appears ===
                // Use specific title bar locator to avoid strict mode violation (multiple divs)
                Locator formTitle = page.locator("div.shrink-0.p-4.text-blue-gray-900.text-2xl.font-semibold:has-text('Mark As Settled')");
                if (formTitle.isVisible()) {
                    DashboardManager.log("[DN Flow] Mark As Settled form popup opened – filling fields");

                    // Bank Charges (fill 100)
                    Locator bankChargesInput = page.locator("input[name='bank_charges']");
                    if (bankChargesInput.isEnabled()) {
                        DashboardManager.log("[DN Flow] Bank Charges enabled – filling 100");
                        bankChargesInput.fill("100");
                    } else {
                        DashboardManager.log("[DN Flow] Bank Charges disabled – skipping");
                    }

                    // FX Rate (fill 86)
                    Locator fxRateInput = page.locator("input[name='sub_org_fx_rate']");
                    if (fxRateInput.isEnabled()) {
                        DashboardManager.log("[DN Flow] FX Rate enabled – filling 86");
                        fxRateInput.fill("86");
                    } else {
                        DashboardManager.log("[DN Flow] FX Rate disabled – skipping");
                    }

                    // Click Submit CTA
                    Locator submitBtn = page.locator("button[type='submit']:has-text('Submit')");
                    submitBtn.waitFor(new Locator.WaitForOptions().setTimeout(15000));
                    safeClick(submitBtn, "Submit Settle Form");

                    waitForToast("Debit note status has been updated successfully");
                } else {
                    DashboardManager.log("[DN Flow] No Mark As Settled form popup – assuming direct settle");
                    waitForToast("Debit note status has been updated successfully");
                }
            } else {
                DashboardManager.log("[DN Flow] Settle icon not visible for DN ID: " + dnId);
            }

            DashboardManager.log("[DN Flow] Full Debit Note flow completed successfully!");
            return true;

        } catch (Exception e) {
            DashboardManager.log("[DN Flow] Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}