package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Paths;

import com.artha.utils.DashboardManager;

public class EscrowTransferUptoProject {

    private final Page page;

    public EscrowTransferUptoProject(Page page) {
        this.page = page;
    }

    private void safeClick(Locator loc, String name) {
        try {
            loc.scrollIntoViewIfNeeded();
            loc.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(25000));
            loc.click(new Locator.ClickOptions().setTimeout(20000));
            DashboardManager.log("[Escrow Project Flow] Clicked: " + name);
        } catch (Exception e) {
            DashboardManager.log("[Escrow Project Flow] Normal click failed for " + name + " - using dispatchEvent");
            loc.evaluate("el => el.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true, view: window}))");
            DashboardManager.log("[Escrow Project Flow] JS dispatchEvent Clicked: " + name);
        }
    }

    private void waitForToast(String expectedMessage) {
        try {
            Locator toast = page.locator("div[role='status']:has-text('" + expectedMessage + "')");
            toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30000));
            String actualText = toast.innerText().trim();
            DashboardManager.log("[Escrow Project Flow] Toast DETECTED: '" + actualText + "' (expected: '" + expectedMessage + "')");
            toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(15000));
            DashboardManager.log("[Escrow Project Flow] Toast verified & hidden: " + expectedMessage);
        } catch (Exception e) {
            DashboardManager.log("[Escrow Project Flow] Toast '" + expectedMessage + "' not detected after 30s - continuing");
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get("escrow-project-toast-missing-" + System.currentTimeMillis() + ".png"))
                    .setFullPage(true));
        }
    }

    private void typeDate(Locator dateInput, String ddMMyyyy) {
        dateInput.click();
        dateInput.pressSequentially(ddMMyyyy, new Locator.PressSequentiallyOptions().setDelay(150));
        page.waitForTimeout(500); // Let auto-cursor movement happen
    }

    /**
     * Creates a new project + sub-project, then transfers escrow from account → new project.
     * @param previousAccountName Account name captured earlier (e.g. "USD ACCOUNT CLINT")
     * @return newly created project name (for future use)
     */
    public String transferEscrowUptoProject(String previousAccountName) {
        String newProjectName = null;

        try {
            DashboardManager.log("[Escrow Project Flow] Starting transfer from Account to new Project...");

            // === STEP 1: GO TO PROJECT MODULE ===
            Locator projectModule = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='/projects'])").first();
            if (projectModule.count() == 0) {
                projectModule = page.locator("a[href*='/projects']:has-text('project')").first();
            }
            safeClick(projectModule, "Project Module");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            // === STEP 2: CREATE NEW PROJECT ===
            Locator createProjectBtn = page.locator("button:has-text('Create Project')").first();
            createProjectBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            safeClick(createProjectBtn, "Create Project CTA");

            page.waitForTimeout(5000); // Modal load

            // Unique Project Name
            newProjectName = "AutoNewProj-" + System.currentTimeMillis();
            Locator projectNameInput = page.locator("input[name='project_name']");
            projectNameInput.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            projectNameInput.fill(newProjectName);
            DashboardManager.log("[Escrow Project Flow] New Project Name: " + newProjectName);

            // === ACCOUNT DROPDOWN - FIXED AS PER PMTProjectCreation ===
            Locator accountInput = page.locator("input[name='project_account_id']");
            safeClick(accountInput, "Account Dropdown");
            accountInput.fill(previousAccountName);
            page.waitForTimeout(1500);

            // Wait for dropdown listbox to appear
            Locator dropdown = page.locator("ul[role='listbox']");
            dropdown.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10000));

            // Directly click the first matching option (no ArrowDown needed)
            Locator option = dropdown.locator("li").first();
            option.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(5000));

            safeClick(option, "Account Dropdown Option: " + previousAccountName);

            page.waitForTimeout(1500); // Wait for selection to apply

            // Contract Start & End Dates
            typeDate(page.locator("input[name='project_contract_start_date']"), "12052025");
            typeDate(page.locator("input[name='project_contract_end_date']"), "12122025");

            // Initial Project Amount
            page.locator("input[name='project_initial_amount']").fill("1000000");

            // Upload Client Contract
            page.locator("input[name='project_upload_client_contract']")
                    .setInputFiles(Paths.get("src/test/resources/demo-client.pdf"));

            // Line of Business - select first visible option
            Locator lobBtn = page.locator("button[name='lob']");
            safeClick(lobBtn, "LOB Dropdown");
            page.locator("ul[role='listbox'] li").first().click();

            // SKU - Public Relations
            Locator skuInput = page.locator("input[id='sub_projects0.sub_project_sku']");
            safeClick(skuInput, "SKU Dropdown");
            skuInput.fill("Public Relations");
            page.waitForTimeout(1000);
            page.keyboard().press("ArrowDown");
            page.keyboard().press("Enter");

            // Vendor - Avenger
            Locator vendorInput = page.locator("input[id='sub_projects0.sub_project_vender']");
            safeClick(vendorInput, "Vendor Dropdown");
            vendorInput.fill("Avenger");
            page.waitForTimeout(1000);
            page.keyboard().press("ArrowDown");
            page.keyboard().press("Enter");

            // Vendor Currency - Indian Rupee
            Locator currencyInput = page.locator("input[id='sub_projects0.sub_project_vendor_currency']");
            safeClick(currencyInput, "Vendor Currency Dropdown");
            currencyInput.fill("Indian Rupee");
            page.waitForTimeout(1000);
            page.keyboard().press("ArrowDown");
            page.keyboard().press("Enter");

            // Vendor Amount
            page.locator("input[name='sub_projects0.subproject_vendor_amount']").fill("800000");

            // Upload Vendor Contract
            page.locator("input[name='sub_projects0.sub_project_upload_vender_contract']")
                    .setInputFiles(Paths.get("src/test/resources/demo-vendor.pdf"));

            // Vendor Contract Start & End Dates
            typeDate(page.locator("input[name='sub_projects0.sub_project_vender_contract_start_date']"), "1205");
            typeDate(page.locator("input[name='sub_projects0.sub_project_vender_contract_end_date']"), "1212");

            // === CLIENT CONTACT - FIXED: NO FILL, JUST CLICK + ARROW DOWN + ENTER ===
            Locator contactInput = page.locator("input[id='sub_projects0.sub_project_contact']");
            safeClick(contactInput, "Client Contact Dropdown");
            page.waitForTimeout(1000);
            page.keyboard().press("ArrowDown");
            page.keyboard().press("Enter");

            // === Sales, Delivery, BA SPOC - fill "bharat" ===
            String[] spocFields = {"sales_spoc", "delivery_spoc", "BA_spoc"};
            for (String field : spocFields) {
                Locator spocInput = page.locator("input[id='sub_projects0.sub_project_" + field + "']");
                safeClick(spocInput, "SPOC Field: " + field);
                spocInput.fill("bharat");
                page.waitForTimeout(1000);
                page.keyboard().press("ArrowDown");
                page.keyboard().press("Enter");
            }

            // Create Project with Sub Project
            Locator createBtn = page.locator("button:has-text('Create Project with Sub Project')").first();
            safeClick(createBtn, "Create Project with Sub Project");

            waitForToast("Project has been created successfully");

            // === CAPTURE NEW PROJECT NAME FROM LISTING ===
            Locator newProjCell = page.locator("td:has-text('" + newProjectName + "')").first();
            if (newProjCell.isVisible()) {
                DashboardManager.log("[Escrow Project Flow] New project created and visible: " + newProjectName);
            } else {
                DashboardManager.log("[Escrow Project Flow] WARNING: New project name not found in listing");
            }

            // === GO TO ACCOUNT MODULE & OPEN PREVIOUS ACCOUNT ===
            Locator accountTab = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='/account'])").first();
            safeClick(accountTab, "Account Tab");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            // === SEARCH FOR ACCOUNT BY NAME, THEN CLICK EYE ICON ===
            Locator searchInput = page.locator("input[placeholder='Search Account ...']").first();
            searchInput.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
            searchInput.click();
            searchInput.fill(previousAccountName);
            DashboardManager.log("[Escrow Project Flow] Searching for account: " + previousAccountName);
            page.waitForTimeout(2000);

            // Wait for filtered results then click the first VisibilityIcon
            Locator viewIcon = page.locator("svg[data-testid='VisibilityIcon']").first();
            viewIcon.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            safeClick(viewIcon, "Open Account: " + previousAccountName);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(3000);

            // === RAISE TRANSFER REQUEST FROM ACCOUNT ===
            Locator transferRequestBtnAccount = page.locator("button:has-text('Transfer Request')").first();
            safeClick(transferRequestBtnAccount, "Transfer Request CTA (from Account)");

            page.waitForTimeout(5000);

            // Choose Project - select the newly created project
            Locator projectDropdownBtn = page.locator("button[name='project']");
            safeClick(projectDropdownBtn, "Choose Project Dropdown");
            page.waitForTimeout(2000);
            Locator projectOption = page.locator("ul[role='listbox'] li:has-text('" + newProjectName + "')").first();
            safeClick(projectOption, "Select New Project: " + newProjectName);

            // PI Dropdown - select TWO PIs (first two available, skip "Select All")
            Locator piInputAccount = page.locator("input#PI").first();
            safeClick(piInputAccount, "PI Dropdown (Account Transfer)");
            page.waitForTimeout(3000); // Wait for dropdown to fully open

            // ArrowDown once → skips "Select All" (highlights first real PI)
            page.keyboard().press("ArrowDown");
            page.waitForTimeout(500);

            // ArrowDown again → highlights second PI (if needed, but we select first now)
            page.keyboard().press("ArrowDown");
            page.waitForTimeout(500);

            // Enter → selects first PI
            page.keyboard().press("Enter");
            page.waitForTimeout(1500); // Wait for selection to apply

            // ArrowDown once more → highlights second PI
            page.keyboard().press("ArrowDown");
            page.waitForTimeout(500);

            // Enter → selects second PI
            page.keyboard().press("Enter");
            page.waitForTimeout(4000); // Longer wait - critical for total/amount update & Confirm button enable

            DashboardManager.log("[Escrow Project Flow] Selected TWO PIs via keyboard (skipped Select All)");

            // Note
            Locator noteFieldAccount = page.locator("textarea[name='Note']").first();
            noteFieldAccount.fill("This is automated Notes");

            // Submit
            Locator submitBtnAccount = page.locator("button:has-text('confirm')").first();
            safeClick(submitBtnAccount, "Confirm CTA (Account → Project)");

            waitForToast("Escrow transfer request raised successfully..!!");

            // === NEW: APPROVE THE ACCOUNT → PROJECT TRANSFER REQUEST ===
            // Give backend time + force table refresh
            page.waitForTimeout(8000);
            page.reload();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            // HOD Approval - wait for table to load data
            Locator requestTab = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='poapproval'])").first();
            safeClick(requestTab, "Requests Tab (for account → project approval)");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(10000);

            Locator transferApprovalTab = page.locator("a:has-text('Transfer Approval')").first();
            safeClick(transferApprovalTab, "Transfer Approval Tab (account → project)");

            // Wait up to 5 attempts for table rows + approve icon to appear
            boolean tableLoaded = false;
            for (int attempt = 1; attempt <= 5 && !tableLoaded; attempt++) {
                page.waitForTimeout(5000);
                Locator tableRows = page.locator("table tbody tr");
                if (tableRows.count() > 0) {
                    tableLoaded = true;
                    DashboardManager.log("[Escrow Project Flow] Transfer Approval table loaded after " + attempt + " attempts");
                } else {
                    DashboardManager.log("[Escrow Project Flow] Waiting for Transfer Approval table... attempt " + attempt);
                }
            }

            // IMPROVED: Find first row with visible approve icon (latest pending)
            Locator hodApproveIcon = page.locator("table tbody tr")
                    .filter(new Locator.FilterOptions().setHas(page.locator("svg[data-testid='CheckCircleOutlineIcon']")))
                    .first()
                    .locator("svg[data-testid='CheckCircleOutlineIcon']");

            if (hodApproveIcon.isVisible()) {
                safeClick(hodApproveIcon, "Approve CTA (HOD) - Account → Project");
                Locator reasonField = page.locator("textarea[name='Reason']");
                reasonField.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
                reasonField.fill("This is automated filled reason");
                Locator submitBtn = page.locator("button:has-text('submit')").first();
                safeClick(submitBtn, "Submit CTA (HOD - account → project)");
                waitForToast("Escrow transfer request approved successfully.");
            } else {
                DashboardManager.log("[Escrow Project Flow] No pending HOD approve icon found after table wait - screenshot");
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("hod-no-approve-account-project-" + System.currentTimeMillis() + ".png"))
                        .setFullPage(true));
            }

            // Finance Approval - same retry logic
            Locator financeTab = page.locator("div.flex.justify-between.items-center.cursor-pointer:has(a[href*='embfinance'])").first();
            safeClick(financeTab, "Finance Tab (for account → project approval)");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(10000);

            Locator escrowTransferTab = page.locator("div:has-text('Escrow Transfer')").first();
            safeClick(escrowTransferTab, "Escrow Transfer Tab (account → project)");

            // Wait up to 5 attempts for table rows + approve icon
            tableLoaded = false;
            for (int attempt = 1; attempt <= 5 && !tableLoaded; attempt++) {
                page.waitForTimeout(5000);
                Locator tableRows = page.locator("table tbody tr");
                if (tableRows.count() > 0) {
                    tableLoaded = true;
                    DashboardManager.log("[Escrow Project Flow] Escrow Transfer table loaded after " + attempt + " attempts");
                } else {
                    DashboardManager.log("[Escrow Project Flow] Waiting for Escrow Transfer table... attempt " + attempt);
                }
            }

            Locator financeApproveIcon = page.locator("table tbody tr")
                    .filter(new Locator.FilterOptions().setHas(page.locator("svg[data-testid='CheckCircleOutlineIcon']")))
                    .first()
                    .locator("svg[data-testid='CheckCircleOutlineIcon']");

            if (financeApproveIcon.isVisible()) {
                safeClick(financeApproveIcon, "Approve CTA (Finance) - Account → Project");
                Locator reasonField = page.locator("textarea[name='Reason']");
                reasonField.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
                reasonField.fill("This is automated reason");
                Locator submitBtn = page.locator("button:has-text('submit')").first();
                safeClick(submitBtn, "Submit CTA (Finance - account → project)");
                waitForToast("Escrow transfer request approved successfully.");
            } else {
                DashboardManager.log("[Escrow Project Flow] No pending Finance approve icon found after table wait - screenshot");
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("finance-no-approve-account-project-" + System.currentTimeMillis() + ".png"))
                        .setFullPage(true));
            }

            DashboardManager.log("[Escrow Project Flow] Full flow completed! New Project: " + newProjectName);
            return newProjectName;

        } catch (Exception e) {
            DashboardManager.log("[Escrow Project Flow] Error: " + e.getMessage());
            e.printStackTrace();
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get("escrow-project-fail-" + System.currentTimeMillis() + ".png"))
                    .setFullPage(true));
            return newProjectName;
        }
    }
}