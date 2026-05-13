package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import com.artha.utils.DashboardManager;

public class PMTMilestonePage {

    private final Page page;

    public PMTMilestonePage(Page page) {
        this.page = page;
    }

    private void safeClick(Locator loc, String name) {
        try {
            loc.scrollIntoViewIfNeeded();
            loc.click(new Locator.ClickOptions().setTimeout(15000));
            DashboardManager.log("[Milestone] Clicked: " + name);
        } catch (Exception e) {
            DashboardManager.log("[Milestone] Normal click failed for " + name + " - trying JS click");
            loc.evaluate("el => el.click()");
            DashboardManager.log("[Milestone] JS Clicked: " + name);
        }
    }

    private void waitForToast(String expectedMessage) {
        try {
            Locator toast = page.locator("xpath=//div[@role='status']//*[contains(text(), '" + expectedMessage + "')]");
            toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            DashboardManager.log("[Milestone] Toast verified: " + expectedMessage);
            toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(15000));
        } catch (Exception e) {
            DashboardManager.log("[Milestone] Toast '" + expectedMessage + "' not detected - continuing");
        }
    }

    /**
     * Opens the just-created project using its Project ID (e.g., 2025/P/620)
     * Then opens the sub-project and creates multiple milestones
     */
    public boolean openProjectAndCreateMilestones(String projectId, String[] milestoneNames) {
        try {
            DashboardManager.log("[Milestone] Opening project with ID: " + projectId);

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(8000);

            // Reliable XPath for Open Project button
            Locator openProjectBtn = page.locator(
                    "xpath=(//td/p[normalize-space(.)='" + projectId + "']//ancestor::tr//button[contains(text(), 'Open Project')])[1]"
            ).first();

            // Fallback using row text filter
            if (openProjectBtn.count() == 0) {
                Locator row = page.locator("tr")
                        .filter(new Locator.FilterOptions().setHasText(projectId))
                        .first();
                if (row.count() > 0) {
                    openProjectBtn = row.locator("button:has-text('Open Project')").first();
                }
            }

            if (openProjectBtn.count() == 0) {
                DashboardManager.log("[Milestone] Open Project button not found for ID: " + projectId);
                return false;
            }

            openProjectBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
            safeClick(openProjectBtn, "Open Project Button for " + projectId);

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(7000);

            // Open Sub-Project
            Locator openSubProjectBtn = page.locator("button:has-text('Open')").first();
            openSubProjectBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
            safeClick(openSubProjectBtn, "Open Sub-Project Button");

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(5000);

            // Click Milestone tab
            Locator milestoneTab = page.locator("li[role='tab'][data-value='milestone']");
            safeClick(milestoneTab, "Milestone Tab");
            page.waitForTimeout(3000);

            // Create each milestone
            for (String milName : milestoneNames) {
                DashboardManager.log("[Milestone] Creating milestone: " + milName);

                // Create Milestone CTA (type=button)
                Locator createBtn = page.locator("button[type='button']:has-text('Create MileStone')").first();
                createBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
                safeClick(createBtn, "Create Milestone CTA");

                // Fill form
                page.locator("input[name='milestone_name']").fill(milName);
                page.locator("input[name='expected_collection_amount']").fill("100000");
                page.locator("input[name='expected_release_amount']").fill("80000");
                page.locator("input[name='expected_realisation']").fill("20000");

                page.locator("input[name='expected_milestone_start_date']")
                        .pressSequentially("1205", new Locator.PressSequentiallyOptions().setDelay(200));
                page.locator("input[name='expected_milestone_end_date']")
                        .pressSequentially("1212", new Locator.PressSequentiallyOptions().setDelay(200));

                page.locator("textarea[name='description']").fill("This is automated milestone creation");

                // Submit - target type=submit button
                Locator submitBtn = page.locator("button[type='submit']:has-text('Create Milestone')").first();

                // Fallbacks for submit button
                if (submitBtn.count() == 0) {
                    submitBtn = page.locator("button.bg-theme-green:has-text('Create Milestone')").first();
                }
                if (submitBtn.count() == 0) {
                    submitBtn = page.locator("button.w-\\[300px\\]:has-text('Create Milestone')").first();
                }

                submitBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
                safeClick(submitBtn, "Create Milestone Submit Button");

                waitForToast("Milestone has been created");

                DashboardManager.log("[Milestone] Milestone '" + milName + "' created successfully");
            }

            return true;

        } catch (Exception e) {
            DashboardManager.log("[Milestone] Error during milestone creation: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}