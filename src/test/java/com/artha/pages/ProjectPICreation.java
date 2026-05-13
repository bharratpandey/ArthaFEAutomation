package com.artha.pages;


import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;


import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.artha.utils.DashboardManager;


public class ProjectPICreation {


    private final Page page;


    public ProjectPICreation(Page page) {
        this.page = page;
    }


    private void safeClick(Locator loc, String name) {
        page.waitForTimeout(2000);
        try {
            loc.scrollIntoViewIfNeeded();
            loc.click();
            DashboardManager.log("[PI Flow] Clicked: " + name);
        } catch (Exception e) {
            try {
                loc.evaluate("el => el.click()");
                DashboardManager.log("[PI Flow] JS Clicked: " + name);
            } catch (Exception ex) {
                DashboardManager.log("[PI Flow] Click failed for " + name + ": " + ex.getMessage());
            }
        }
    }


    // Updated waitForToast – now waits for toast to appear AND disappear
    private void waitForToast(String message) {
        try {
            Locator toastLocator = page.locator(
                            "xpath=//div[contains(@role, 'status')]//b[contains(text(), '" + message + "')] | " +
                                    "//div[contains(text(), '" + message + "')]")
                    .first();


            // Wait for toast to appear
            toastLocator.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(15000));


            DashboardManager.log("[PI Flow] Toast Verified: " + message);


            // Wait for toast to disappear – this ensures UI has fully settled
            toastLocator.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(10000));


            DashboardManager.log("[PI Flow] Toast dismissed – UI stable");


        } catch (Exception e) {
            DashboardManager.log("[PI Flow] Warning: Toast '" + message + "' not detected or didn't dismiss (continuing).");
        }
    }




    // =================================================================
    // 1. CREATE PIs, PUSH TO ZOHO, AND CAPTURE IDs
    // =================================================================
    public List<String> createAndPushPIs(String[] milestones) {
        List<String> createdPiIds = new ArrayList<>();




        try {
            DashboardManager.log("[PI Flow] Starting PI Creation Sequence...");




            // 1. Click PI Tab
            Locator piTab = page.locator("li[data-value='pi']").first();
            safeClick(piTab, "PI Tab");


            // Wait for Create PI button to confirm tab is loaded
            page.locator("div[data-value='pi'] button:has-text('Create PI')")
                    .first()
                    .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));


            String futureDate = LocalDate.now().plusDays(30).toString();


            for (String milName : milestones) {
                DashboardManager.log("--------------------------------------------------");
                DashboardManager.log("[PI Flow] Processing Milestone: " + milName);


                // --- 2. Create PI ---
                Locator createBtn = page.locator("div[data-value='pi'] button:has-text('Create PI')").first();
                safeClick(createBtn, "Create PI CTA");


                // Fill Form
                page.locator("input#Milestone").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
                Locator milInput = page.locator("input#Milestone");
                milInput.click();
                milInput.type(milName, new Locator.TypeOptions().setDelay(100));
                page.waitForTimeout(800);
                page.keyboard().press("ArrowDown");
                page.keyboard().press("Enter");


                page.locator("input[name='PI_amount_exclusive_tax']").fill("100000");
                page.locator("input[name='expected_shipment_date']").fill(futureDate);


                Locator submitBtn = page.locator("button[type='submit']").filter(new Locator.FilterOptions().setHasText("Create")).first();
                safeClick(submitBtn, "Submit PI");


                waitForToast("Sales Order has been created successfully");


                // --- 3. CAPTURE ID ---
                Locator piTableRows = page.locator("div[data-value='pi'] table tbody tr");
                piTableRows.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));


                Locator latestIdCell = piTableRows.first().locator("td").nth(1);
                String piId = latestIdCell.innerText().trim();


                if (!piId.isEmpty()) {
                    DashboardManager.log("[PI Flow] CAPTURED PI ID: " + piId);
                    createdPiIds.add(piId);
                } else {
                    System.err.println("[PI Flow] Failed to capture PI ID for " + milName);
                }


                // --- 4. Push to Zoho ---
                Locator generatePiIcon = piTableRows.first().locator("svg[data-testid='SendTimeExtensionIcon']").first();


                if (generatePiIcon.isVisible()) {
                    safeClick(generatePiIcon, "Push to Zoho Icon");
                    waitForToast("Sales Order has been pushed into ZohoBooks");
                } else {
                    DashboardManager.log("[PI Flow] Zoho Push icon not visible on row 1.");
                }
            }
            return createdPiIds;


        } catch (Exception e) {
            DashboardManager.log("[PI Flow] Error creating PIs: " + e.getMessage());
            e.printStackTrace();
            return createdPiIds;
        }
    }


    // =================================================================
    // 2. FINANCE MODULE: PROCESS SPECIFIC IDs (Generate CI -> Pay)
    // =================================================================
    public boolean processFinanceInvoices(List<String> piIds) {
        if (piIds.isEmpty()) {
            DashboardManager.log("[PI Flow] No PI IDs provided to process.");
            return false;
        }


        try {
            DashboardManager.log("[PI Flow] Navigating to Finance Module...");
            Locator financeNav = page.locator("a[href*='/embfinance']").first();
            if (financeNav.count() == 0) financeNav = page.locator("text=finance").first();
            safeClick(financeNav, "Finance Nav");
            page.waitForLoadState(LoadState.NETWORKIDLE);


            Locator soModule = page.locator("a[href*='/pifinance']").first();
            if (soModule.count() == 0) soModule = page.locator("div:has-text('Sales Orders')").first();
            safeClick(soModule, "Sales Orders Module");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(2000);


            String todayDate = LocalDate.now().toString();


            DashboardManager.log("[PI Flow] Processing IDs: " + piIds);


            for (String targetId : piIds) {
                DashboardManager.log("--------------------------------------------------");
                DashboardManager.log("[PI Flow] Finding Row for PI ID: " + targetId);


                Locator targetRow = page.locator("xpath=//td[normalize-space()='" + targetId + "']/ancestor::tr").first();


                try {
                    targetRow.waitFor(new Locator.WaitForOptions().setTimeout(5000));
                } catch (Exception e) {
                    DashboardManager.log("[PI Flow] Row with ID " + targetId + " NOT FOUND in Finance List.");
                    continue;
                }


                // --- STEP A: GENERATE INVOICE (CI) ---
                Locator generateCiIcon = targetRow.locator("svg[data-testid='ReceiptIcon']");


                if (generateCiIcon.isVisible()) {
                    DashboardManager.log("[PI Flow] Generating Invoice for " + targetId);
                    safeClick(generateCiIcon, "Generate Invoice Icon");


                    Locator confirmBtn = page.locator("button").filter(new Locator.FilterOptions().setHasText("Confirm")).first();
                    confirmBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
                    safeClick(confirmBtn, "Confirm Button");


                    waitForToast("Invoice has been created");
                } else {
                    DashboardManager.log("[PI Flow] Generate Icon not visible for " + targetId + " (Check if already generated)");
                }


                // --- STEP B: MARK AS PAID ---
                targetRow = page.locator("xpath=//td[normalize-space()='" + targetId + "']/ancestor::tr").first();
                Locator paidIcon = targetRow.locator("svg[data-testid='PaidIcon']");

                if (paidIcon.isVisible()) {
                    DashboardManager.log("[PI Flow] Marking Payment for " + targetId);
                    safeClick(paidIcon, "Mark as Paid Icon");

                    Locator transInput = page.locator("input[name='transaction_id']");
                    transInput.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));

                    // CI Amount
                    Locator paidAmountInput = page.locator("input[name='amount']");
                    if (paidAmountInput.isEnabled()) {
                        DashboardManager.log("[PI Flow] CI Amount field enabled – clearing & filling 100000");
                        paidAmountInput.clear();
                        paidAmountInput.fill("100000");
                    } else {
                        DashboardManager.log("[PI Flow] CI Amount field disabled – skipping");
                    }

                    transInput.fill("CMS-AUTO-" + targetId);

                    page.locator("input[name='received_on']").fill(todayDate);

                    // Tax Amount
                    Locator taxInput = page.locator("input[name='tax']").first();
                    if (taxInput.isVisible() && taxInput.isEnabled()) {
                        taxInput.fill("18000");
                        DashboardManager.log("[PI Flow] Tax amount filled: 18000");
                    } else {
                        DashboardManager.log("[PI Flow] Tax input not editable — skipping");
                    }

                    // FX Rate
                    Locator fxRateInput = page.locator("input[name='sub_org_fx_rate']");
                    if (fxRateInput.isVisible() && fxRateInput.isEditable()) {
                        DashboardManager.log("[PI Flow] FX Rate field is editable – filling 86");
                        fxRateInput.fill("86");
                    } else {
                        DashboardManager.log("[PI Flow] FX Rate field is disabled/readonly – skipping");
                    }

                    // Bank Charges
                    Locator bankChargesInput = page.locator("input[name='bank_charges']");
                    if (bankChargesInput.isVisible() && bankChargesInput.isEditable()) {
                        DashboardManager.log("[PI Flow] Bank Charges field is editable – filling 100");
                        bankChargesInput.fill("100");
                    } else {
                        DashboardManager.log("[PI Flow] Bank Charges field is disabled/readonly – skipping");
                    }

                    Locator updateBtn = page.locator("button[type='submit']")
                            .filter(new Locator.FilterOptions().setHasText("Update")).first();
                    safeClick(updateBtn, "Update Payment");

                    waitForToast("Payment has been created");

                    targetRow = page.locator("xpath=//td[normalize-space()='" + targetId + "']/ancestor::tr").first();
                    DashboardManager.log("[PI Flow] Payment completed and row refreshed for ID: " + targetId);

                } else {
                    DashboardManager.log("[PI Flow] Paid Icon not visible for " + targetId);
                }

            } // ← closes for loop

            return true;

        } catch (Exception e) {
            DashboardManager.log("[PI Flow] Finance Processing Failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }}