package com.artha.tests;

import com.artha.utils.DashboardManager;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.artha.pages.*;
import org.junit.jupiter.api.*;

import java.nio.file.Paths;

public class PMTProjectTest {

    private Playwright playwright;
    private Browser browser;
    private Page page;
    private PMTProjectCreation projectCreation;
    private PMTMilestonePage milestonePage;
    private PMTPICreation piCreation;
    private PMTPOCreation poCreation;
    private PMTCreditNote creditNote;
    private PMTDebitNote debitNote;  // ← Added

    @BeforeEach
    void setup() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setSlowMo(500));
        page = browser.newPage();
        page.navigate("https://dev-kam-v2.emb.global/login");
        projectCreation = new PMTProjectCreation(page);
        milestonePage = new PMTMilestonePage(page);
        piCreation = new PMTPICreation(page);
        poCreation = new PMTPOCreation(page);
        creditNote = new PMTCreditNote(page);
        debitNote = new PMTDebitNote(page);  // ← Added
    }

    @Test
    void testFullProjectFlow() {
        String uniqueProjectName = "AutoProject-" + System.currentTimeMillis();

        // 1. Project Creation + Login
        boolean projectSuccess = projectCreation.loginAndCreateProject(uniqueProjectName);
        Assertions.assertTrue(projectSuccess, "Project creation failed!");

        // Capture Project ID (e.g., 2025/P/640)
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(8000);
        Locator projectIdCell = page.locator("td p:has-text('2025/P/')").first();
        String projectId = projectIdCell.innerText().trim();
        DashboardManager.log("[TEST] Captured Project ID: " + projectId);

        // 2. Create Milestones (Mil1 to Mil4)
        String[] milestones = {"Mil1", "Mil2", "Mil3", "Mil4"};
        boolean milestoneSuccess = milestonePage.openProjectAndCreateMilestones(projectId, milestones);
        Assertions.assertTrue(milestoneSuccess, "Milestone creation failed!");

        // 3. PI Creation + Finance Flow
        boolean piSuccess = piCreation.createPIAndCompleteFinanceFlow();
        Assertions.assertTrue(piSuccess, "PI + Finance flow failed!");

        // 4. PO Creation + Full Flow (using only Mil1 & Mil2)
        String[] poMilestones = {"Mil1", "Mil2"};
        boolean poSuccess = poCreation.createAndCompletePOFlow(projectId, poMilestones);
        Assertions.assertTrue(poSuccess, "PO flow failed!");

        // 5. Credit Note Flow (pass the first PI ID if needed, or skip if not required)
        String firstPiId = "4730"; // Replace with actual captured PI ID from PI flow
        boolean cnSuccess = creditNote.createAndCompleteCreditNoteFlow(projectId, firstPiId);
        Assertions.assertTrue(cnSuccess, "Credit Note flow failed!");

        // 6. Debit Note Flow - Added
        boolean dnSuccess = debitNote.createAndCompleteDebitNoteFlow(projectId);
        Assertions.assertTrue(dnSuccess, "Debit Note flow failed!");

        // Final screenshot
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get("screenshots/full-project-flow-" + uniqueProjectName + ".png"))
                .setFullPage(true));

        DashboardManager.log("[TEST] Full Project → Milestone → PI → PO → Credit Note → Debit Note flow completed successfully!");
    }

    @AfterEach
    void teardown() {
        if (page != null) page.close();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
}