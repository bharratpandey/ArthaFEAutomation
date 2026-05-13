package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.regex.Pattern;

import com.artha.utils.DashboardManager;

public class ProjectMilestonePage {

    private final Page page;

    public ProjectMilestonePage(Page page) {
        this.page = page;
    }

    private void safeClick(Locator loc, String name) {
        try {
            loc.scrollIntoViewIfNeeded();
            loc.click();
            DashboardManager.log("[Project] Clicked: " + name);
        } catch (Exception e) {
            loc.evaluate("el => el.click()");
            DashboardManager.log("[Project] JS Clicked: " + name);
        }
    }

    // =====================================================
    // 1. NAVIGATE TO PROJECT MODULE & OPEN DEAL PROJECT
    // =====================================================
    public boolean navigateToProjectAndOpen(String dealName) {
        try {
            DashboardManager.log("[Project] Navigating to Project Module...");

            // Click Project Module in Sidebar
            Locator projectNav = page.locator("a[href*='/projects']").first();
            safeClick(projectNav, "Project Module Nav");

            page.waitForLoadState();
            page.waitForTimeout(1500); // Wait for list

            // Find row/card with Deal Name and click "Open Project"
            DashboardManager.log("[Project] Searching for project: " + dealName);

// Search for the project by name
            Locator searchInput = page.locator("input[placeholder='Search Project ...']").first();
            searchInput.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            searchInput.click();
            searchInput.fill(dealName);
            page.waitForTimeout(2000); // wait for list to load

// Click Open Project button in the filtered result
            Locator openProjectBtn = page.locator("tr")
                    .filter(new Locator.FilterOptions().setHas(page.locator("span[aria-label='" + dealName + "']")))
                    .locator("button:has-text('Open Project')")
                    .first();

            if (openProjectBtn.count() == 0) {
                // Fallback: just click first visible Open Project button after search
                openProjectBtn = page.locator("button:has-text('Open Project')").first();
            }

            if (openProjectBtn.count() > 0) {
                safeClick(openProjectBtn, "Open Project Button");
            } else {
                DashboardManager.log("[Project] Project not found for deal: " + dealName);
                return false;
            }

            page.waitForLoadState();
            page.waitForTimeout(1000);

            // Open Sub-Project (Click the "Open" button on the first sub-project card)
            Locator openSubProjectBtn = page.locator("button:has-text('Open')").first();
            safeClick(openSubProjectBtn, "Open Sub-Project");

            page.waitForLoadState();
            return true;

        } catch (Exception e) {
            DashboardManager.log("[Project] Navigation Failed: " + e.getMessage());
            return false;
        }
    }

    // =====================================================
    // 2. CREATE MILESTONES
    // =====================================================
    public boolean createMilestonesSeq() {
        try {
            // Go to Milestone Tab
            Locator milestoneTab = page.locator("li[data-value='milestone']").first();
            safeClick(milestoneTab, "Milestone Tab");
            page.waitForTimeout(800);

            // Create 4 Milestones
            createSingleMilestone("Mil1", "100000", "80000", "20000");
            createSingleMilestone("Mil2", "100000", "80000", "20000");
            createSingleMilestone("Mil3", "100000", "80000", "20000");
            createSingleMilestone("Mil4", "100000", "80000", "20000");

            return true;
        } catch (Exception e) {
            DashboardManager.log("[Project] Milestone Creation Sequence Failed: " + e.getMessage());
            return false;
        }
    }

    private void createSingleMilestone(String name, String colAmt, String relAmt, String realAmt) {
        DashboardManager.log("[Project] Creating Milestone: " + name);

        // Click "Create Milestone" CTA
        Locator createBtn = page.locator("button:has-text('Create MileStone')").first();
        safeClick(createBtn, "Create Milestone CTA");

        // Wait for modal input
        page.locator("input[name='milestone_name']").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));

        // Fill Form
        page.locator("input[name='milestone_name']").fill(name);
        page.locator("input[name='expected_collection_amount']").fill(colAmt);
        page.locator("input[name='expected_release_amount']").fill(relAmt);
        page.locator("input[name='expected_realisation']").fill(realAmt);

        // Dates: HTML is type='date', so we use ISO format YYYY-MM-DD
        page.locator("input[name='expected_milestone_start_date']").fill("2025-05-12");
        page.locator("input[name='expected_milestone_end_date']").fill("2025-12-12");

        page.locator("textarea[name='description']").fill("this is automated milestone Creation");

        // Submit
        Locator submitBtn = page.locator("button[type='submit']").filter(new Locator.FilterOptions().setHasText("Create Milestone")).first();
        safeClick(submitBtn, "Submit Milestone");

        // Wait for Toast
        try {
            page.locator("div[role='status']").filter(new Locator.FilterOptions().setHasText("Milestone has been created"))
                    .first()
                    .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            DashboardManager.log("[Project] Toast Verified: Milestone has been created");

            // Wait for toast to disappear or modal to close before next loop
            page.waitForTimeout(1500);
        } catch (Exception e) {
            DashboardManager.log("[Project] Warning: Milestone Toast not detected.");
        }
    }
}