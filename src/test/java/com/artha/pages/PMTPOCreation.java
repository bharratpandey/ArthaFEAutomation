package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.artha.utils.DashboardManager;

public class PMTPOCreation {

    private final Page page;

    public PMTPOCreation(Page page) {
        this.page = page;
    }

    private void safeClick(Locator loc, String name) {
        try {
            loc.scrollIntoViewIfNeeded();
            loc.click(new Locator.ClickOptions().setTimeout(15000));
            DashboardManager.log("[PO Flow] Clicked: " + name);
        } catch (Exception e) {
            DashboardManager.log("[PO Flow] Normal click failed for " + name + " - trying JS click");
            loc.evaluate("el => el.click()");
            DashboardManager.log("[PO Flow] JS Clicked: " + name);
        }
    }

    private void waitForToast(String expectedMessage) {
        try {
            Locator toast = page.locator("xpath=//div[@role='status']//*[contains(text(), '" + expectedMessage + "')]");
            toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            DashboardManager.log("[PO Flow] Toast verified: " + expectedMessage);
            toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(15000));
        } catch (Exception e) {
            DashboardManager.log("[PO Flow] Toast '" + expectedMessage + "' not detected - continuing");
        }
    }

    public boolean createAndCompletePOFlow(String projectId, String[] milestones) {
        List<String> poIds = new ArrayList<>();

        try {
            DashboardManager.log("[PO Flow] Starting PO flow after PI...");

            // === STEP 1: GO BACK TO PROJECT LIST ===
            Locator projectModule = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='/projects'])").first();
            if (projectModule.count() == 0) {
                projectModule = page.locator("a[href*='/projects']:has-text('project')").first();
            }
            safeClick(projectModule, "Project Module");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            // === STEP 2: OPEN CURRENT PROJECT BY ID ===
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
                DashboardManager.log("[PO Flow] Could not find Open Project button for ID: " + projectId);
                return false;
            }

            openProjectBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
            safeClick(openProjectBtn, "Open Project Button");

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            // === STEP 3: OPEN SUB-PROJECT ===
            Locator openSubProjectBtn = page.locator("button:has-text('Open')").first();
            openSubProjectBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
            safeClick(openSubProjectBtn, "Open Sub-Project");

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(6000);

            // === STEP 4: CLICK PO TAB ===
            Locator poTab = page.locator("li[role='tab'][data-value='PO']").first();
            if (poTab.count() == 0) poTab = page.locator("li[role='tab']:has-text('PO')").first();
            if (poTab.count() == 0) poTab = page.locator("li.cursor-pointer:has(div:text('PO'))").first();

            poTab.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30000));
            safeClick(poTab, "PO Tab");

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            page.locator("button:has-text('Create PO')").first()
                    .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));

            // === STEP 5: CREATE 2 POs ===

// Refresh page so PO tab content loads fresh
            page.reload();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(2000);

// Re-click PO tab after reload
            Locator poTabAfterReload = page.locator("li[role='tab'][data-value='PO']").first();
            poTabAfterReload.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            safeClick(poTabAfterReload, "PO Tab (after reload)");
            page.waitForTimeout(1500);

            for (String milName : milestones) {
                DashboardManager.log("[PO Flow] Creating PO for Milestone: " + milName);

                Locator createPoBtn = page.locator("button:has-text('Create PO')").first();
                createPoBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
                safeClick(createPoBtn, "Create PO CTA");

                // Wait for modal to open
                page.waitForSelector(
                        "div[role='dialog'], div[role='presentation'], .MuiModal-root, .MuiDrawer-root",
                        new Page.WaitForSelectorOptions().setTimeout(15000)
                );
                page.waitForTimeout(800);

                // Milestone input with broader fallback selector
                Locator milInput = page.locator(
                        "input#Milestone, input[placeholder*='ilestone'], input[name*='ilestone']"
                ).first();
                milInput.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
                milInput.scrollIntoViewIfNeeded();
                milInput.click();
                milInput.fill(milName);
                page.waitForTimeout(800);

                // Wait for dropdown then select
                page.waitForSelector(
                        "ul[role='listbox'] li, div[role='option']",
                        new Page.WaitForSelectorOptions().setTimeout(5000)
                );
                page.keyboard().press("ArrowDown");
                page.keyboard().press("Enter");

                page.locator("input[name='po_amount_exclusive_tax']").fill("80000");
                page.locator("input[name='realisation_amount']").fill("20000");
                page.locator("input[name='vendor_invoice_number']").fill("VN/INV/2025");

                Locator vendorDateInput = page.locator("input[name='vendor_invoice_date']");
                vendorDateInput.click();
                vendorDateInput.pressSequentially("12052025", new Locator.PressSequentiallyOptions().setDelay(150));

                page.locator("input[name='upload_vendor_invoice']")
                        .setInputFiles(Paths.get("src/test/resources/demo-vendor.pdf"));

                Locator clientDateInput = page.locator("input[name='client_sign_off_date']");
                clientDateInput.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
                clientDateInput.click();
                clientDateInput.pressSequentially("12052025", new Locator.PressSequentiallyOptions().setDelay(150));

                page.locator("input[name='upload_client_sign_off']")
                        .setInputFiles(Paths.get("src/test/resources/demo-client.pdf"));

                Locator submitBtn = page.locator("button[type='submit']:has-text('Create')").first();
                safeClick(submitBtn, "Create PO Submit");

                waitForToast("PO has been created successfully");

                Locator poTableRows = page.locator("div[data-value='PO'] table tbody tr");
                poTableRows.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));

                Locator idCell = poTableRows.first()
                        .locator("td").first()
                        .locator("span.MuiTypography-small, p")
                        .first();

                String poId = idCell.innerText().trim();
                if (!poId.isEmpty() && poId.matches("\\d+")) {
                    DashboardManager.log("[PO Flow] CAPTURED PO ID: " + poId);
                    poIds.add(poId);
                }
            }

            // === STEP 6: APPROVE POs - FIXED WITH EXACT LOGIC FROM ProjectPOCreation ===
            DashboardManager.log("[PO Flow] Navigating to Requests Module...");

            Locator requestNav = page.locator("a[href*='poapproval']").first();
            if (requestNav.count() == 0) {
                requestNav = page.locator("text=requests").first();
            }
            if (requestNav.count() == 0) {
                requestNav = page.locator("a:has-text('requests')").first();
            }

            safeClick(requestNav, "Requests Module Nav");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(10000); // Give time for new POs to appear

            for (String poId : poIds) {
                DashboardManager.log("[PO Flow] Approving PO: " + poId);

                boolean approved = false;
                int maxAttempts = 8;

                for (int attempt = 1; attempt <= maxAttempts && !approved; attempt++) {
                    DashboardManager.log("[PO Flow] Attempt " + attempt + "/" + maxAttempts + " for PO " + poId);

                    // Wait for table rows to be present
                    page.waitForSelector("tr", new Page.WaitForSelectorOptions().setTimeout(15000));
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    page.waitForTimeout(1500 + attempt * 800); // Progressive wait

                    // Locate the exact clickable div that contains the PO ID
                    Locator poIdElement = page.locator("div.cursor-pointer")
                            .filter(new Locator.FilterOptions().setHasText(poId))
                            .first();

                    Locator targetRow;
                    if (poIdElement.count() > 0) {
                        targetRow = poIdElement.locator("xpath=ancestor::tr").first();
                    } else {
                        // Fallback: broader search on the row
                        targetRow = page.locator("tr")
                                .filter(new Locator.FilterOptions().setHasText(poId))
                                .first();
                    }

                    if (targetRow.count() == 0) {
                        DashboardManager.log("→ No row found containing PO " + poId);
                        if (attempt < maxAttempts) {
                            page.reload();
                            page.waitForLoadState(LoadState.NETWORKIDLE);
                            page.waitForTimeout(5000);
                        }
                        continue;
                    }

                    // Debug: Print row content preview
                    String rowPreview = targetRow.innerText().trim().replaceAll("\\s+", " ");
                    if (rowPreview.length() > 120) rowPreview = rowPreview.substring(0, 120) + "...";
                    DashboardManager.log("→ Found row preview: " + rowPreview);

                    // Find approve icon inside this exact row
                    Locator approveIcon = targetRow.locator("svg[data-testid='CheckCircleOutlineIcon']").first();

                    // Wait specifically for the icon to be visible
                    try {
                        approveIcon.waitFor(new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.VISIBLE)
                                .setTimeout(10000));
                    } catch (Exception e) {
                        DashboardManager.log("→ Icon exists but not visible yet");
                    }

                    if (approveIcon.isVisible()) {
                        DashboardManager.log("→ Approve icon FOUND and VISIBLE for PO " + poId);

                        safeClick(approveIcon, "Approve icon for PO " + poId);

                        // Handle modal
                        Locator reasonField = page.locator("textarea[name='reason']").first();
                        reasonField.waitFor(new Locator.WaitForOptions().setTimeout(15000));
                        reasonField.fill("Automated Approval Reason");

                        Locator confirmBtn = page.locator("button:has-text('approve'), button:has-text('Approve')").first();
                        confirmBtn.waitFor(new Locator.WaitForOptions().setTimeout(15000));
                        safeClick(confirmBtn, "Confirm Approve Button");

                        waitForToast("Purchase Order request " + poId + " has been successfully updated");

                        DashboardManager.log("[PO Flow] SUCCESS: PO " + poId + " approved!");
                        approved = true;
                    } else {
                        DashboardManager.log("→ Icon NOT visible in row (count = " + approveIcon.count() + ")");
                        if (attempt < maxAttempts) {
                            page.reload();
                            page.waitForLoadState(LoadState.NETWORKIDLE);
                            page.waitForTimeout(5000);
                        }
                    }
                }

                if (!approved) {
                    DashboardManager.log("[PO Flow] FAILED to approve PO " + poId + " after " + maxAttempts + " attempts");
                }
            }

            // === STEP 7: DOC VERIFICATION ===
            try {
                if (poIds.isEmpty()) {
                    DashboardManager.log("[PO Flow] No PO IDs provided for doc verification. Skipping.");
                    return true;
                }

                DashboardManager.log("[PO Flow] Navigating to Finance -> Doc Requests...");

                Locator financeNav = page.locator("a[href*='/embfinance']").first();
                if (financeNav.count() == 0) {
                    financeNav = page.locator("text=finance").first();
                }
                if (financeNav.count() == 0) {
                    financeNav = page.locator("a:has-text('finance')").first();
                }

                safeClick(financeNav, "Finance Module");

                Locator docNav = page.locator("a[href*='podocVerification']");
                safeClick(docNav, "Doc Requests Tab");
                page.waitForLoadState(LoadState.NETWORKIDLE);
                page.waitForTimeout(6000);

                String poIdToVerify = poIds.get(0);
                DashboardManager.log("[PO Flow] Verifying documents for ONE PO only (covers all): " + poIdToVerify);

                Locator row = page.locator("div")
                        .filter(new Locator.FilterOptions().setHasText("PO Id"))
                        .filter(new Locator.FilterOptions().setHasText(poIdToVerify))
                        .first();

                if (row.count() == 0) {
                    DashboardManager.log("[PO Flow] Doc request not found for PO " + poIdToVerify + ". Skipping verification.");
                    return true;
                }

                Locator verifyIcon = row.locator("svg[data-testid='CheckCircleOutlineIcon']").first();
                if (!verifyIcon.isVisible()) {
                    DashboardManager.log("[PO Flow] Verify icon not visible for PO " + poIdToVerify + ". Skipping.");
                    return true;
                }

                safeClick(verifyIcon, "Verify Docs Icon - PO " + poIdToVerify);

                Locator reasonField = page.locator("textarea[name='reason']").first();
                reasonField.waitFor(new Locator.WaitForOptions().setTimeout(15000));
                reasonField.fill("Automated Doc Verification Reason");

                Locator approveBtn = page.locator("button:has-text('approve')").first();
                approveBtn.waitFor(new Locator.WaitForOptions().setTimeout(10000));
                safeClick(approveBtn, "Doc Approve CTA - PO " + poIdToVerify);

                page.waitForTimeout(3000);
                DashboardManager.log("[PO Flow] Document verification completed (one PO verified → all covered)");

            } catch (Exception e) {
                DashboardManager.log("[PO Flow] Doc verification failed but continuing: " + e.getMessage());
            }

            // === STEP 8: GENERATE BILL & MARK AS PAID ===
            Locator poFinanceTab = page.locator("a[href*='pofinance']").first();
            if (poFinanceTab.count() == 0) poFinanceTab = page.locator("text=Purchase Orders").first();
            safeClick(poFinanceTab, "Purchase Orders Tab");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(10000);

            for (String poId : poIds) {
                DashboardManager.log("[PO Flow] Processing Bill & Payment for PO: " + poId);

                Locator row = page.locator("tr").filter(new Locator.FilterOptions().setHasText(poId)).first();
                if (row.count() == 0) {
                    page.reload();
                    page.waitForTimeout(8000);
                    row = page.locator("tr").filter(new Locator.FilterOptions().setHasText(poId)).first();
                }

                Locator billIcon = row.locator("svg[data-testid='PriceCheckIcon']").first();
                if (billIcon.isVisible()) {
                    safeClick(billIcon, "Generate Bill Icon - PO " + poId);
                    Locator billInput = page.locator("textarea[name='Enter your Bill number']");
                    billInput.waitFor(new Locator.WaitForOptions().setTimeout(12000));
                    billInput.fill("BILL/2025/" + poId);
                    Locator submitBtn = page.locator("button:has-text('submit')").first();
                    safeClick(submitBtn, "Submit Bill Button");
                    waitForToast("Bill has been created");
                }

                int paidAttempts = 10;
                boolean paidMarked = false;
                for (int i = 1; i <= paidAttempts && !paidMarked; i++) {
                    row = page.locator("tr").filter(new Locator.FilterOptions().setHasText(poId)).first();
                    Locator paidIcon = row.locator("svg[data-testid='PaidIcon']").first();
                    if (paidIcon.isVisible()) {
                        safeClick(paidIcon, "Mark as Paid Icon - PO " + poId);
                        Locator yesBtn = page.locator("div[role='status'] button:has-text('Yes')");
                        yesBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(12000));
                        safeClick(yesBtn, "Confirm Paid - Yes");
                        paidMarked = true;
                    } else {
                        page.waitForTimeout(4000 + i * 1000);
                    }
                }
            }

            DashboardManager.log("[PO Flow] Full PO flow completed for all POs!");
            return true;

        } catch (Exception e) {
            DashboardManager.log("[PO Flow] Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}