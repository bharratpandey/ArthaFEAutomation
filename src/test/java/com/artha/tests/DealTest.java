package com.artha.tests;


import com.artha.pages.*;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.Test;


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import com.artha.utils.DashboardManager;

import static org.junit.jupiter.api.Assertions.*;


public class DealTest extends BaseTest {


    private static final String DEV_URL = "https://dev-kam-v2.emb.global/login";
    private static final String TEST_EMAIL = "Bharat.pandey@emb.global";
    private static final String TEST_PASSWORD = "Bharat@123";


    private static final String SUPPLY_URL = "https://dev-supply.emb.global/login";
    private static final String SUPPLY_EMAIL = "psnayal08@gmail.com";
    private static final String SUPPLY_PASSWORD = "1234";


    private PMTCreditNote creditNote;
    private PMTDebitNote debitNote;


    @Test
    void createDealHappyPath() {

        DashboardManager.log("[REPORT] 🚀 Starting E2E Journey...");

        // ================= LOGIN =================
        LoginPage login = new LoginPage(page);
        login.navigateTo(DEV_URL);
        assertTrue(login.loginAndVerify(TEST_EMAIL, TEST_PASSWORD));


        DealPage deals = new DealPage(page);


        // ================= CREATE DEAL =================
        deals.openDealsFromNav();
        deals.clickCreateDeal();


        String dealName = "AutoDeal-" + System.currentTimeMillis();


        deals.fillDealForm(
                dealName,
                "Bharat pandey Account", //INR client ACCOUNT,USD ACCOUNT CLINT
                "1000000",
                "",
                "Public Relations",
                "Cloud",
                "bharat",
                "bharat",
                "CONSULTANT",
                "emb global- gurugram"
        );


        deals.submitDeal();


        assertTrue(
                verifyDealCreatedRobust(dealName, Duration.ofSeconds(20).toMillis()),
                "Deal not created"
        );


        sleepMs(300);


        // ================= PARTNER ALIGNMENT =================
        assertTrue(deals.moveToPartnerAlignment(
                dealName,
                "java,html,css",
                "client ref text",
                "notes here",
                Paths.get("src/test/resources/demo-proposal.pdf")
        ));


        // Toast Check
        try {
            DashboardManager.log("[TEST] Waiting for Partner Alignment toast...");
            page.getByText("The deal is now on partner alignment stage")
                    .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            DashboardManager.log("[TEST] ✅ Toast verified.");
        } catch (Exception e) {
            DashboardManager.log("[TEST] ⚠️ Warning: Toast message not detected.");
        }


        // ================= SUPPLY FLOW =================
        Page supplyPage = page.context().newPage();
        SupplyPage supply = new SupplyPage(supplyPage);


        supply.loginSupply(SUPPLY_URL, SUPPLY_EMAIL, SUPPLY_PASSWORD);
        supply.refreshOnce();
        sleepMs(900);


        supply.openVendorShortlistModule();
        assertTrue(supply.searchDealByNumericFragment(dealName));
        assertTrue(supply.clickShortlistForDeal(dealName));


        assertTrue(
                supply.selectVendorAndSubmit("Rohan Test 10 July")
                        || supply.selectVendorByNameAndSubmit("Rohan Test 10 July")
        );


        supply.close();


        // ================= BACK TO KAM =================
        page.bringToFront();
        page.reload();
        sleepMs(700);


        deals.openDealsFromNav();
        sleepMs(400);
        deals.openDealFromListing(dealName);


        // ================= PARTNER SHORTLISTED =================
        StagePage stage = new StagePage(page);
        assertTrue(stage.partnerShortlisting(
                "Rohan Test 10 July",
                "this is automated flow Test",
                "Indian Rupee",  //United States Dollar,United Arab Emirates Dirham
                "800000"
        ));


        // ================= KYC & DOCUMENTATION =================
        KycDocumentationPage kyc = new KycDocumentationPage(page);
        assertTrue(kyc.completeKycAndDocumentation(
                "src/test/resources/demo-client.pdf",
                "12/05/2025",
                "12/12/2025",
                "src/test/resources/demo-vendor.pdf",
                "12/05/2025",
                "12/12/2025",
                "This is automated flow note",
                dealName
        ));


        sleepMs(800);


        // ================= FINANCE → DOC REQUESTS =================
        DashboardManager.log("[TEST] Navigating to Finance -> Doc Requests");


        Locator financeNav = page.locator("a[href*='/embfinance']").first();
        if (financeNav.count() > 0) {
            financeNav.click();
        } else {
            page.locator("text=finance").first().click();
        }
        page.waitForTimeout(900);


        Locator docReq = page.locator("a[href*='podocVerification']").first();
        if (docReq.count() == 0) {
            docReq = page.locator("a:has-text('Doc Requests')").first();
        }
        docReq.click();
        page.waitForTimeout(900);


        page.locator("li[data-value='deal_doc']").click();
        page.waitForTimeout(700);


        // ================= CLOSED WON =================
        DashboardManager.log("[TEST] Moving deal to CLOSED WON");


        DealClosedWonPage closedWon = new DealClosedWonPage(page);


        boolean won = closedWon.moveDealToClosedWon(
                dealName,
                "Bharat",       // Sales SPOC
                "0 Days",       // Vendor Credit Period
                "This is notes for moving deal to closed won stage"
        );


        assertTrue(won, "Deal failed to move to CLOSED WON");


        sleepMs(800);


        // ================= PROJECT CREATED =================
        DashboardManager.log("[TEST] Moving deal to PROJECT CREATED");


        DealProjectCreatedPage projectCreated = new DealProjectCreatedPage(page);


        boolean projectOk = projectCreated.moveDealToProjectCreated(
                "Bharat",   // Delivery SPOC
                "This is the notes for moving deal to project creation"
        );


        assertTrue(projectOk, "Deal failed to move to PROJECT CREATED");


        sleepMs(1000);


        // ================= PROJECT MILESTONES =================
        DashboardManager.log("[TEST] Starting Project Milestone Creation");


        ProjectMilestonePage projectPage = new ProjectMilestonePage(page);


        // 1. Navigate to Project Module & Open the specific project
        assertTrue(projectPage.navigateToProjectAndOpen(dealName), "Failed to open Project");


        // 2. Create 3 Milestones (M1, Mil 2, Mil 3)
        assertTrue(projectPage.createMilestonesSeq(), "Milestone creation failed");


        // ================= PROJECT PI & FINANCE FLOW =================
        DashboardManager.log("[TEST] Starting PI Creation -> Zoho -> Finance CI -> Payment");


        ProjectPICreation piPage = new ProjectPICreation(page);


        String[] milestonesToBill = {"Mil1", "Mil2", "Mil3"};


        // 1. Create PIs and Capture the IDs
        List<String> createdPiIds = piPage.createAndPushPIs(milestonesToBill);


        assertFalse(createdPiIds.isEmpty(), "No PI IDs were captured during creation!");
        DashboardManager.log("[TEST] IDs to process in Finance: " + createdPiIds);


        // 2. Go to Finance Module -> Process specifically those IDs
        assertTrue(piPage.processFinanceInvoices(createdPiIds), "Finance Invoice/Payment flow failed");


        // ================= PROJECT PO & FINANCE FLOW =================
        DashboardManager.log("[TEST] Starting PO Creation -> Request Approval -> Doc Verification -> Bill & Payment");


        ProjectPOCreation poPage = new ProjectPOCreation(page);


        String[] milestonesForPO = {"Mil1", "Mil2"};
        List<String> createdPoIds = poPage.createPOs(dealName, milestonesForPO);


        assertFalse(createdPoIds.isEmpty(), "No PO IDs were captured!");
        DashboardManager.log("[TEST] PO IDs to process: " + createdPoIds);


        assertTrue(poPage.approveRequests(createdPoIds), "PO Request Approval failed");
        assertTrue(poPage.verifyDocsFinance(createdPoIds), "Finance Doc Verification failed");
        assertTrue(poPage.generateBillAndPay(createdPoIds), "Finance Bill/Payment flow failed");


        // ================= CREDIT NOTE FLOW =================
        DashboardManager.log("[TEST] Starting Credit Note Flow");


        creditNote = new PMTCreditNote(page);


        // Use first PI ID from PI creation (fallback to empty if list is empty)
        String firstPiId = createdPiIds.isEmpty() ? "" : createdPiIds.get(0);


        boolean cnSuccess = creditNote.createAndCompleteCreditNoteFlow(dealName, firstPiId);
        assertTrue(cnSuccess, "Credit Note flow failed!");


        // ================= DEBIT NOTE FLOW =================
        DashboardManager.log("[TEST] Starting Debit Note Flow");


        debitNote = new PMTDebitNote(page);
        boolean dnSuccess = debitNote.createAndCompleteDebitNoteFlow(dealName);
        assertTrue(dnSuccess, "Debit Note flow failed!");


        DashboardManager.log("✅ [TEST] Completed Full Deal → Project → PI → PO → Credit Note → Debit Note Flow for: " + dealName);


        // ================= ESCROW TRANSFER UP TO ACCOUNT =================
        DashboardManager.log("[TEST] Starting Escrow Transfer up to Account");
        EscrowTransferUptoAccount escrow = new EscrowTransferUptoAccount(page);
        String capturedAccountName = escrow.transferEscrowUptoAccount(dealName);
        assertNotNull(capturedAccountName, "Escrow transfer failed or account name not captured!");
        DashboardManager.log("[TEST] ✅ Captured Account Name: " + capturedAccountName);
        sleepMs(2000);

        // After EscrowTransferUptoAccount completes
        DashboardManager.log("[TEST] Starting Escrow Transfer to New Project");
        EscrowTransferUptoProject escrowProject = new EscrowTransferUptoProject(page);
        String newProjectName = escrowProject.transferEscrowUptoProject(capturedAccountName);
        assertNotNull(newProjectName, "Failed to create new project or capture name");
        DashboardManager.log("[TEST] ✅ New Project Created: " + newProjectName);
        sleepMs(2000);

        // After EscrowTransferUptoProject completes
        DashboardManager.log("[TEST] Starting Escrow Transfer to Sub-Project");
        EscrowTransferUptoSubProject escrowSub = new EscrowTransferUptoSubProject(page);
        String finalProjectId = escrowSub.transferEscrowUptoSubProject(newProjectName);
        assertNotNull(finalProjectId, "Failed to transfer to sub-project");
        DashboardManager.log("[TEST] ✅ Escrow transferred to Sub-Project under: " + finalProjectId);
        sleepMs(2000);

        DashboardManager.log("[REPORT] ✅ Full E2E Journey Completed Successfully!");
    }


    // ================= HELPERS =================


    private boolean verifyDealCreatedRobust(String dealName, long timeoutMs) {
        try {
            page.waitForSelector(
                    "h3:has-text(\"" + dealName + "\")",
                    new Page.WaitForSelectorOptions().setTimeout(timeoutMs)
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    private void saveDebugArtifacts(String name) {
        try {
            Path dir = Paths.get("target/debug", name);
            Files.createDirectories(dir);
            page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png")));
            Files.writeString(dir.resolve("page.html"), page.content());
        } catch (Exception ignored) {}
    }


    private void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }
}