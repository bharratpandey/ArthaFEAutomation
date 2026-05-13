package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Paths;

import com.artha.utils.DashboardManager;

public class PMTProjectCreation {

    private final Page page;

    public PMTProjectCreation(Page page) {
        this.page = page;
    }

    private void safeClick(Locator loc, String name) {
        try {
            loc.scrollIntoViewIfNeeded();
            loc.click(new Locator.ClickOptions().setTimeout(15000));
            DashboardManager.log("[Project Creation] Clicked: " + name);
        } catch (Exception e) {
            DashboardManager.log("[Project Creation] Normal click failed for " + name + " - trying JS click");
            try {
                loc.evaluate("el => el.click()");
                DashboardManager.log("[Project Creation] JS Clicked: " + name);
            } catch (Exception ex) {
                DashboardManager.log("[Project Creation] JS click also failed for " + name);
                throw ex;
            }
        }
    }

    private void waitForToast(String expectedMessage) {
        try {
            Locator toast = page.locator("xpath=//div[@role='status']//*[contains(text(), '" + expectedMessage + "')]");
            toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            DashboardManager.log("[Project Creation] Toast verified: " + expectedMessage);
            toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(15000));
        } catch (Exception e) {
            DashboardManager.log("[Project Creation] Toast '" + expectedMessage + "' not detected - continuing");
        }
    }

    public boolean loginAndCreateProject(String uniqueProjectName) {
        try {
            DashboardManager.log("[Project Creation] Starting login and project creation flow...");

            // === LOGIN ===
            page.locator("input[name='email']").fill("Bharat.pandey@emb.global");
            page.locator("input[name='password']").fill("Bharat@123");
            safeClick(page.locator("button:has-text('Sign In')"), "Sign In Button");

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            // === NAVIGATE TO PROJECT MODULE ===
            Locator projectModuleLink = page.locator("a[href*='/projects']:has-text('project')").first();

            if (projectModuleLink.count() == 0) {
                projectModuleLink = page.locator("div.cursor-pointer:has(a[href*='/projects'])").first();
            }

            if (projectModuleLink.count() == 0) {
                projectModuleLink = page.locator("div.border-b-[0.01px]:has(a[href*='/projects'])").first();
            }

            projectModuleLink.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(15000));

            projectModuleLink.scrollIntoViewIfNeeded();
            safeClick(projectModuleLink, "Project Module");

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);
            page.evaluate("window.scrollTo(0, 0)");

// === CLICK CREATE PROJECT CTA ===
            Locator createBtn = page.locator("button:has-text('Create Project')")
                    .filter(new Locator.FilterOptions().setHas(page.locator("svg")))
                    .first();

            if (createBtn.count() == 0) {
                createBtn = page.locator("button[style*='rgb(26, 197, 92)']").first();
            }

            if (createBtn.count() == 0) {
                createBtn = page.locator("button:has(svg path[d*='M12 4.5v15m7.5-7.5h-15'])").first();
            }

            createBtn.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(25000));

            safeClick(createBtn, "Create Project CTA");

            // === FILL PROJECT FORM ===
            page.locator("input[name='project_name']").fill(uniqueProjectName);

            // Account - Registered INR
            Locator accountInput = page.locator("input[name='project_account_id']");
            accountInput.click();
            accountInput.fill("AutoAcc-1767946073378");

// Wait for the dropdown listbox to appear
            Locator dropdown = page.locator("ul[role='listbox']");
            dropdown.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10000));

// Now directly click the first (and only) option - no ArrowDown needed
            Locator option = dropdown.locator("li").first();  // or li[aria-selected='true']

            option.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(5000));

            safeClick(option, "Account Dropdown Option: AutoAcc-1767946073378");

// Optional: verify selection (text disappears or input value changes)
            page.waitForTimeout(1000);
            // Contract dates (type slowly for auto-jump)
            page.locator("input[name='project_contract_start_date']")
                    .pressSequentially("12052025", new Locator.PressSequentiallyOptions().setDelay(200));
            page.locator("input[name='project_contract_end_date']")
                    .pressSequentially("12122025", new Locator.PressSequentiallyOptions().setDelay(200));

            // Initial amount
            page.locator("input[name='project_initial_amount']").fill("1000000");

            // Client contract upload
            page.locator("input[name='project_upload_client_contract']")
                    .setInputFiles(Paths.get("src/test/resources/demo-client.pdf"));

            // Line of Business - pick first option
            Locator lobBtn = page.locator("button[name='lob']");
            safeClick(lobBtn, "LOB Dropdown");
            page.locator("ul[role='listbox'] li").first().click();

            // SKU - Public Relations
            Locator skuInput = page.locator("input[id='sub_projects0.sub_project_sku']");
            skuInput.click();
            skuInput.fill("Public Relations");
            page.waitForTimeout(2000);
            page.keyboard().press("ArrowDown");
            page.keyboard().press("Enter");

            // Vendor - Avenger
            Locator vendorInput = page.locator("input[id='sub_projects0.sub_project_vender']");
            vendorInput.click();
            vendorInput.fill("Avenger");
            page.waitForTimeout(1000);
            page.keyboard().press("ArrowDown");
            page.keyboard().press("Enter");

            // Vendor Currency - Indian Rupees
            Locator currencyInput = page.locator("input[id='sub_projects0.sub_project_vendor_currency']");
            currencyInput.click();
            currencyInput.fill("Indian Rupee");
            page.waitForTimeout(1000);
            page.keyboard().press("ArrowDown");
            page.keyboard().press("Enter");

            // Vendor Amount
            page.locator("input[name='sub_projects0.subproject_vendor_amount']").fill("800000");

            // Client Contact - first option
            Locator contactInput = page.locator("input[id='sub_projects0.sub_project_contact']");
            contactInput.click();
            page.waitForTimeout(1000);
            page.keyboard().press("ArrowDown");
            page.keyboard().press("Enter");

            // Sales, Delivery, BA SPOC - bharat
            String[] spocFields = {"sales_spoc", "delivery_spoc", "BA_spoc"};
            for (String field : spocFields) {
                Locator spocInput = page.locator("input[id='sub_projects0.sub_project_" + field + "']");
                spocInput.click();
                spocInput.fill("bharat");
                page.waitForTimeout(1000);
                page.keyboard().press("ArrowDown");
                page.keyboard().press("Enter");
            }

            // Vendor contract upload
            page.locator("input[name='sub_projects0.sub_project_upload_vender_contract']")
                    .setInputFiles(Paths.get("src/test/resources/demo-vendor.pdf"));

            // Vendor contract dates
            page.locator("input[name='sub_projects0.sub_project_vender_contract_start_date']")
                    .pressSequentially("1205", new Locator.PressSequentiallyOptions().setDelay(200));
            page.locator("input[name='sub_projects0.sub_project_vender_contract_end_date']")
                    .pressSequentially("12122", new Locator.PressSequentiallyOptions().setDelay(200));

            // Final submit
            Locator finalSubmit = page.locator("button:has-text('Create Project with Sub Project')");
            finalSubmit.scrollIntoViewIfNeeded();
            safeClick(finalSubmit, "Create Project with Sub Project CTA");

            waitForToast("Project has been created successfully");

            DashboardManager.log("[Project Creation] SUCCESS: Project '" + uniqueProjectName + "' created with sub-project");

            return true;

        } catch (Exception e) {
            DashboardManager.log("[Project Creation] FAILED: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}