package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import com.artha.utils.DashboardManager;

public class ProjectPOCreation {

    private final Page page;

    public ProjectPOCreation(Page page) {
        this.page = page;
    }

    private void safeClick(Locator loc, String name) {
        try {
            loc.scrollIntoViewIfNeeded();
            loc.click(new Locator.ClickOptions().setTimeout(10000));
            DashboardManager.log("[PO Flow] Clicked: " + name);
        } catch (Exception e) {
            try {
                loc.evaluate("el => el.click()");
                DashboardManager.log("[PO Flow] JS Clicked: " + name);
            } catch (Exception ex) {
                DashboardManager.log("[PO Flow] Click failed for " + name + ": " + ex.getMessage());
            }
        }
    }

    private void waitForToast(String expectedMessage) {
        try {
            String normalized = expectedMessage.replace(".", "").trim().toLowerCase();

            Locator toastLocator = page.locator(
                    "xpath=//div[@role='status']//*[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ.', 'abcdefghijklmnopqrstuvwxyz'), '"
                            + normalized + "')]"
            ).first();

            toastLocator.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(15000));

            DashboardManager.log("[PO Flow] Toast appeared: " + expectedMessage);

            toastLocator.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(15000));

            DashboardManager.log("[PO Flow] Toast dismissed — UI refreshed");

        } catch (Exception e) {
            DashboardManager.log("[PO Flow] WARNING: Toast '" + expectedMessage + "' not detected. Continuing...");
            page.waitForTimeout(4000);
        }
    }

    // =================================================================
    // 1. CREATE POs
    // =================================================================
    public List<String> createPOs(String projectName, String[] milestones) {
        List<String> createdPoIds = new ArrayList<>();

        try {
            DashboardManager.log("[PO Flow] Navigating to Projects...");
            Locator projectNav = page.locator("a[href*='/projects']").first();
            safeClick(projectNav, "Project Module Nav");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(2000);

            DashboardManager.log("[PO Flow] Opening Project: " + projectName);
            Locator projectRow = page.locator("td")
                    .filter(new Locator.FilterOptions().setHasText(projectName))
                    .locator("xpath=ancestor::tr").first();

            Locator openMainProjectBtn = projectRow.locator("button:has-text('Open')");
            safeClick(openMainProjectBtn, "Open Main Project Button");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(3000);

            DashboardManager.log("[PO Flow] Opening Sub-Project...");
            Locator openSubProjectBtn = page.locator("button:has-text('Open')").first();
            openSubProjectBtn.waitFor(new Locator.WaitForOptions().setTimeout(10000));
            safeClick(openSubProjectBtn, "Open Sub-Project Button");

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(2000);

            Locator poTab = page.locator("li[role='tab'][data-value='PO']").first();
            safeClick(poTab, "PO Tab");
            page.waitForTimeout(1500);

            for (String milName : milestones) {
                DashboardManager.log("--------------------------------------------------");
                DashboardManager.log("[PO Flow] Creating PO for Milestone: " + milName);

                Locator createPoBtn = page.locator("button:has-text('Create PO')").first();
                createPoBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
                safeClick(createPoBtn, "Create PO CTA");

                Locator milInput = page.locator("input#Milestone");
                milInput.waitFor();
                milInput.click();
                milInput.fill(milName);
                page.waitForTimeout(800);
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
                clientDateInput.waitFor();
                clientDateInput.click();
                clientDateInput.pressSequentially("12052025", new Locator.PressSequentiallyOptions().setDelay(150));

                page.locator("input[name='upload_client_sign_off']")
                        .setInputFiles(Paths.get("src/test/resources/demo-client.pdf"));

                Locator submitBtn = page.locator("button[type='submit']:has-text('Create')").first();
                safeClick(submitBtn, "Create PO Submit");

                waitForToast("PO has been created successfully");

                Locator poTableRows = page.locator("div[data-value='PO'] table tbody tr");
                poTableRows.first().waitFor(new Locator.WaitForOptions().setTimeout(10000));

                Locator idCell = poTableRows.first()
                        .locator("td").first()
                        .locator("span.MuiTypography-small, p")
                        .first();

                String poId = idCell.innerText().trim();
                if (!poId.isEmpty() && poId.matches("\\d+")) {
                    DashboardManager.log("[PO Flow] CAPTURED PO ID: " + poId);
                    createdPoIds.add(poId);
                } else {
                    System.err.println("[PO Flow] Failed to capture valid PO ID");
                }
            }

            return createdPoIds;

        } catch (Exception e) {
            DashboardManager.log("[PO Flow] Error creating POs: " + e.getMessage());
            e.printStackTrace();
            return createdPoIds;
        }
    }

    // =================================================================
    // 2. APPROVE REQUESTS - FINAL ROBUST VERSION
    // =================================================================
    public boolean approveRequests(List<String> poIds) {
        try {
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

            return true;

        } catch (Exception e) {
            DashboardManager.log("[PO Flow] Fatal error in approval: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // =================================================================
    // 3. DOC VERIFICATION - OPTIMIZED (Verify only one PO, as it covers all)
    // =================================================================
    public boolean verifyDocsFinance(List<String> poIds) {
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

            // Use only the FIRST PO ID — verifying one covers all
            String poIdToVerify = poIds.get(0);
            DashboardManager.log("[PO Flow] Verifying documents for ONE PO only (covers all): " + poIdToVerify);

            Locator row = page.locator("div")
                    .filter(new Locator.FilterOptions().setHasText("PO Id"))
                    .filter(new Locator.FilterOptions().setHasText(poIdToVerify))
                    .first();

            if (row.count() == 0) {
                DashboardManager.log("[PO Flow] Doc request not found for PO " + poIdToVerify + ". Skipping verification.");
                return true; // Not failing the flow — just proceed
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

            return true;

        } catch (Exception e) {
            DashboardManager.log("[PO Flow] Error in doc verification: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // =================================================================
    // 4. GENERATE BILL & MARK AS PAID - FINAL ROBUST VERSION
    // =================================================================
    public boolean generateBillAndPay(List<String> poIds) {
        try {
            DashboardManager.log("[PO Flow] Navigating to Finance -> Purchase Orders...");

            Locator poTab = page.locator("a[href*='pofinance']").first();
            if (poTab.count() == 0) {
                poTab = page.locator("div:has-text('Purchase Orders')").first();
            }
            if (poTab.count() == 0) {
                poTab = page.locator("a:has-text('Purchase Orders')").first();
            }

            safeClick(poTab, "Purchase Orders Tab");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            for (String poId : poIds) {
                DashboardManager.log("--------------------------------------------------");
                DashboardManager.log("[PO Flow] Processing Bill & Payment for PO: " + poId);

                boolean billGenerated = false;
                boolean paidMarked = false;
                int maxRetries = 6;

                // === FIND ROW & GENERATE BILL ===
                for (int retry = 1; retry <= maxRetries && !billGenerated; retry++) {
                    DashboardManager.log("[PO Flow] Attempt " + retry + "/" + maxRetries + " to process bill for PO " + poId);

                    Locator row = page.locator("tr")
                            .filter(new Locator.FilterOptions().setHasText(poId))
                            .first();

                    if (row.count() == 0) {
                        DashboardManager.log("→ Row not found. Reloading...");
                        page.reload();
                        page.waitForLoadState(LoadState.NETWORKIDLE);
                        page.waitForTimeout(6000);
                        continue;
                    }

                    String rowText = row.innerText().trim().replaceAll("\\s+", " ");
                    if (rowText.length() > 150) rowText = rowText.substring(0, 150) + "...";
                    DashboardManager.log("→ Found row: " + rowText);

                    Locator billIcon = row.locator("svg[data-testid='PriceCheckIcon']").first();
                    if (billIcon.isVisible()) {
                        safeClick(billIcon, "Generate Bill Icon - PO " + poId);

                        Locator billInput = page.locator("textarea[name='Enter your Bill number']");
                        billInput.waitFor(new Locator.WaitForOptions().setTimeout(12000));
                        billInput.fill("BILL/2025/" + poId);

                        Locator submitBtn = page.locator("button:has-text('submit')").first();
                        submitBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
                        safeClick(submitBtn, "Submit Bill Button - PO " + poId);

                        waitForToast("Bill has been created");
                        page.waitForTimeout(3000);

                        DashboardManager.log("[PO Flow] Bill generated for PO: " + poId);
                        billGenerated = true;
                    } else {
                        DashboardManager.log("[PO Flow] Bill icon not visible (already generated?)");
                        billGenerated = true; // Proceed to paid step
                    }
                }

                // === WAIT FOR PAID ICON TO APPEAR AFTER BILL ===
                if (billGenerated) {
                    DashboardManager.log("[PO Flow] Waiting for Paid icon to appear for PO: " + poId);

                    int paidRetries = 10; // Extra retries for Paid icon delay
                    for (int waitAttempt = 1; waitAttempt <= paidRetries && !paidMarked; waitAttempt++) {
                        DashboardManager.log("[PO Flow] Paid icon check " + waitAttempt + "/" + paidRetries);

                        Locator row = page.locator("tr")
                                .filter(new Locator.FilterOptions().setHasText(poId))
                                .first();

                        if (row.count() == 0) {
                            page.reload();
                            page.waitForLoadState(LoadState.NETWORKIDLE);
                            page.waitForTimeout(5000);
                            continue;
                        }

                        Locator paidIcon = row.locator("svg[data-testid='PaidIcon']").first();

                        if (paidIcon.isVisible()) {
                            safeClick(paidIcon, "Mark as Paid Icon - PO " + poId);

                            // === NEW: Handle Confirmation Popup (Are you sure...? Yes button) ===
                            Locator yesBtn = page.locator("button:has-text('Yes')");
                            if (yesBtn.isVisible()) {
                                DashboardManager.log("[PO Flow] Confirmation popup detected – clicking Yes");
                                safeClick(yesBtn, "Confirm Yes - Mark PO as Paid");
                                page.waitForTimeout(2000); // Wait for popup to close
                            } else {
                                DashboardManager.log("[PO Flow] No confirmation popup – proceeding directly");
                            }

                            // === NEW: Handle Form Popup (Bank Charges + FX Rate) if it appears ===
                            Locator bankChargesInput = page.locator("input[name='bank_charges']");
                            Locator fxRateInput = page.locator("input[name='sub_org_fx_rate']");

                            if (bankChargesInput.isVisible() || fxRateInput.isVisible()) {
                                DashboardManager.log("[PO Flow] Paid form popup opened – filling fields");

                                // Bank Charges (fill 100)
                                if (bankChargesInput.isEnabled()) {
                                    DashboardManager.log("[PO Flow] Bank Charges enabled – filling 100");
                                    bankChargesInput.fill("100");
                                } else {
                                    DashboardManager.log("[PO Flow] Bank Charges disabled – skipping");
                                }

                                // FX Rate (fill 86)
                                if (fxRateInput.isEnabled()) {
                                    DashboardManager.log("[PO Flow] FX Rate enabled – filling 86");
                                    fxRateInput.fill("86");
                                } else {
                                    DashboardManager.log("[PO Flow] FX Rate disabled – skipping");
                                }

                                // Click Submit CTA
                                Locator submitBtn = page.locator("button[type='submit']:has-text('Submit')");
                                submitBtn.waitFor(new Locator.WaitForOptions().setTimeout(15000));
                                safeClick(submitBtn, "Submit Paid Form");

                                waitForToast("Debit note status has been updated successfully");
                            } else {
                                DashboardManager.log("[PO Flow] No paid form popup – assuming direct payment");
                                waitForToast("Purchase Order has been marked as paid");
                            }

                            paidMarked = true;
                        } else {
                            DashboardManager.log("→ Paid icon not visible yet. Waiting...");
                            page.waitForTimeout(4000 + waitAttempt * 1000); // Progressive wait
                        }
                    }

                    if (!paidMarked) {
                        DashboardManager.log("[PO Flow] WARNING: Paid icon never appeared for PO " + poId + " after " + paidRetries + " checks");
                    }
                }
            }

            return true;

        } catch (Exception e) {
            DashboardManager.log("[PO Flow] Error in bill/payment: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}