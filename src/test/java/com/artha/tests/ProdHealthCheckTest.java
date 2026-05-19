package com.artha.tests;

import com.artha.pages.ProdHealthCheckPage;
import com.artha.pages.ProdHealthCheckPage.ApiResult;
import com.artha.utils.DashboardManager;
import com.artha.utils.ProdEmailSender;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProdHealthCheckTest
 *
 * Browser-based (Playwright) API + UI sanity check for Artha / EMB production.
 *
 *   ✔  Launches real Chromium (headed — watch it live; pass -Dheadless=true for CI)
 *   ✔  46 steps covering the complete navigation flow
 *   ✔  Auto-captures every API call via network listener (status + timing)
 *   ✔  Saves Playwright trace ZIP  →  target/artha-prod-health-trace.zip
 *   ✔  Writes ExtentReports HTML  →  target/AutomationDashboard.html
 *   ✔  Sends styled HTML email with report attached via Artha EmailSender
 *
 * Run:
 *   mvn test -Dtest=ProdHealthCheckTest
 *   mvn test -Dtest=ProdHealthCheckTest -Dheadless=true        (CI)
 *   mvn test -Dtest=ProdHealthCheckTest -Demail.to=x@y.com    (override recipient)
 *
 * NOTE — known expected 400s:
 *   Steps 20, 21, 22  (/poapproval, /hodescapproval, /escapproval)
 *   This user has no access to those approval modules; 400 is intentional.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProdHealthCheckTest {

    // -----------------------------------------------------------------------
    // Credentials
    // -----------------------------------------------------------------------
    private static final String EMAIL    = "Bharat.pandey@emb.global";
    private static final String PASSWORD = "Bharat@123";

    // -----------------------------------------------------------------------
    // Email recipients — comma-separated, override with -Demail.to=...
    // Default: send to multiple people
    // -----------------------------------------------------------------------
    private static final String EMAIL_TO = System.getProperty(
            "email.to",
            "bharat.pandey@emb.global,saumya.gupta@emb.global,ashish.mishra@emb.global"
    );

    // -----------------------------------------------------------------------
    // Playwright objects  (shared for the full test class)
    // -----------------------------------------------------------------------
    private static Playwright         playwright;
    private static Browser            browser;
    private static BrowserContext     context;
    private static com.microsoft.playwright.Page page;
    private static ProdHealthCheckPage healthCheck;

    private static final String TRACE_PATH = "target/artha-prod-health-trace.zip";

    // -----------------------------------------------------------------------
    // @BeforeAll — init report, launch Chrome, start trace
    // -----------------------------------------------------------------------
    @BeforeAll
    static void setUp() {
        // ── ExtentReports ───────────────────────────────────────────────────
        DashboardManager.initReport();
        DashboardManager.startTest("Artha Prod Health Check — Full Flow (46 steps)");
        DashboardManager.log("Environment : Production — artha.emb.global");
        DashboardManager.log("User        : " + EMAIL);
        DashboardManager.log("Report path : " + DashboardManager.REPORT_PATH);
        DashboardManager.log("Trace path  : " + TRACE_PATH);

        // ── Browser ─────────────────────────────────────────────────────────
        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "true"));
        playwright = Playwright.create();
        browser    = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(headless)
                        .setSlowMo(200)
        );
        context = browser.newContext(
                new Browser.NewContextOptions().setViewportSize(1440, 900)
        );

        // ── Playwright Trace ─────────────────────────────────────────────────
        context.tracing().start(
                new Tracing.StartOptions()
                        .setScreenshots(true)
                        .setSnapshots(true)
        );

        page        = context.newPage();
        healthCheck = new ProdHealthCheckPage(page);

        DashboardManager.log("✅ Chrome launched  (headless=" + headless + ")");
        DashboardManager.log("✅ Playwright trace started");
    }

    // -----------------------------------------------------------------------
    // @AfterAll — stop trace, flush report, send email, close browser
    // -----------------------------------------------------------------------
    @AfterAll
    static void tearDown() {

        // ── Print API summary to console + report ────────────────────────────
        logApiSummary();

        // ── Stop trace ──────────────────────────────────────────────────────
        try {
            context.tracing().stop(
                    new Tracing.StopOptions().setPath(Paths.get(TRACE_PATH)));
            DashboardManager.log("✅ Trace saved → " + TRACE_PATH
                    + "  (view at https://trace.playwright.dev/)");
        } catch (Exception e) {
            DashboardManager.log("⚠️ Trace save failed: " + e.getMessage());
        }

        // ── Close browser ────────────────────────────────────────────────────
        try { if (page      != null) page.close();       } catch (Exception ignored) {}
        try { if (context   != null) context.close();    } catch (Exception ignored) {}
        try { if (browser   != null) browser.close();    } catch (Exception ignored) {}
        try { if (playwright!= null) playwright.close(); } catch (Exception ignored) {}

        // ── Flush ExtentReports HTML ─────────────────────────────────────────
        DashboardManager.flushReport();
        DashboardManager.log("✅ HTML report flushed → " + DashboardManager.REPORT_PATH);

        // ── Send email ───────────────────────────────────────────────────────
        try {
            long slowCount = healthCheck.getResults().stream()
                    .filter(r -> r.elapsedMs > 1000).count();
            ProdEmailSender.sendHealthCheckEmail(EMAIL_TO, slowCount);
        } catch (Exception e) {
            DashboardManager.log("⚠️ Email send failed: " + e.getMessage());
        }
    }

    // ================================================================== //
    //  STEP 01 — Login
    // ================================================================== //

    @Test @Order(1)
    @DisplayName("Step 01 — Login")
    void step01_login() {
        section("STEP 01 — Login");
        healthCheck.login(EMAIL, PASSWORD);
        assertFalse(page.url().contains("/login"),
                "Login failed — still on /login. Check credentials.");
        DashboardManager.log("✅ Step 01 PASSED — Login");
    }

    // ================================================================== //
    //  STEPS 02 – 10  (HRMS / Core modules)
    // ================================================================== //

    @Test @Order(2)
    @DisplayName("Step 02 — HRMS > Sub-Organisations")
    void step02_hrmsSubOrganizations() {
        section("STEP 02 — HRMS > Sub-Organisations");
        healthCheck.checkHrmsSubOrganizations();
        assertApiSuccess("/get_sub_organizations");
        DashboardManager.log("✅ Step 02 PASSED");
    }

    @Test @Order(3)
    @DisplayName("Step 03 — HRMS > Teams")
    void step03_hrmsTeams() {
        section("STEP 03 — HRMS > Teams");
        healthCheck.checkHrmsTeams();
        assertApiSuccess("/search/teams");
        DashboardManager.log("✅ Step 03 PASSED");
    }

    @Test @Order(4)
    @DisplayName("Step 04 — HRMS > Designations")
    void step04_hrmsDesignations() {
        section("STEP 04 — HRMS > Designations");
        healthCheck.checkHrmsDesignations();
        assertApiSuccess("/roles/");
        DashboardManager.log("✅ Step 04 PASSED");
    }

    @Test @Order(5)
    @DisplayName("Step 05 — HRMS > Users")
    void step05_hrmsUsers() {
        section("STEP 05 — HRMS > Users");
        healthCheck.checkHrmsUsers();
        assertApiSuccess("/users/");
        DashboardManager.log("✅ Step 05 PASSED");
    }

    @Test @Order(6)
    @DisplayName("Step 06 — Accounts")
    void step06_accounts() {
        section("STEP 06 — Accounts");
        healthCheck.checkAccounts();
        assertApiSuccess("/accounts/");
        DashboardManager.log("✅ Step 06 PASSED");
    }

    @Test @Order(7)
    @DisplayName("Step 07 — Contacts")
    void step07_contacts() {
        section("STEP 07 — Contacts");
        healthCheck.checkContacts();
        assertApiSuccess("/contacts/");
        DashboardManager.log("✅ Step 07 PASSED");
    }

    @Test @Order(8)
    @DisplayName("Step 08 — Deals")
    void step08_deals() {
        section("STEP 08 — Deals");
        healthCheck.checkDeals();
        assertApiSuccess("/deal/get_all_deals");
        DashboardManager.log("✅ Step 08 PASSED");
    }

    @Test @Order(9)
    @DisplayName("Step 09 — Business Analyst")
    void step09_businessAnalyst() {
        section("STEP 09 — Business Analyst");
        healthCheck.checkBusinessAnalyst();
        assertApiSuccess("/deal/scoping");
        DashboardManager.log("✅ Step 09 PASSED");
    }

    @Test @Order(10)
    @DisplayName("Step 10 — Projects list")
    void step10_projectsList() {
        section("STEP 10 — Projects list");
        healthCheck.checkProjectsList();
        assertApiSuccess("/project/get_projects/");
        DashboardManager.log("✅ Step 10 PASSED");
    }

    // ================================================================== //
    //  STEPS 11 – 19  (Sub-project deep dive + Cloud Billing + Process Notes)
    // ================================================================== //

    @Test @Order(11)
    @DisplayName("Step 11 — Open project: EV Spare Parts Ordering")
    void step11_openProject() {
        section("STEP 11 — Open project");
        healthCheck.openProject();
        assertApiSuccess("/project/2272");
        DashboardManager.log("✅ Step 11 PASSED");
    }

    @Test @Order(12)
    @DisplayName("Step 12 — Open Subject CAT (sub-project 2346)")
    void step12_openSubProject() {
        section("STEP 12 — Open sub-project 2346");
        healthCheck.openSubProject();
        assertApiSuccess("/project/sub_project/2272/2346");
        DashboardManager.log("✅ Step 12 PASSED");
    }

    @Test @Order(13)
    @DisplayName("Step 13 — Milestone tab")
    void step13_milestoneTab() {
        section("STEP 13 — Milestone tab");
        healthCheck.checkMilestoneTab();
        assertApiSuccess("/milestone/get_milestones/2346");
        DashboardManager.log("✅ Step 13 PASSED");
    }

    @Test @Order(14)
    @DisplayName("Step 14 — PI tab")
    void step14_piTab() {
        section("STEP 14 — PI tab");
        healthCheck.checkPiTab();
        assertApiSuccess("/sale_order/get_sale_orders/2346");
        DashboardManager.log("✅ Step 14 PASSED");
    }

    @Test @Order(15)
    @DisplayName("Step 15 — PO tab")
    void step15_poTab() {
        section("STEP 15 — PO tab");
        healthCheck.checkPoTab();
        assertApiSuccess("/purchase_order/get_purchase_orders/2346");
        DashboardManager.log("✅ Step 15 PASSED");
    }

    @Test @Order(16)
    @DisplayName("Step 16 — Credit Note tab")
    void step16_creditNoteTab() {
        section("STEP 16 — Credit Note tab");
        healthCheck.checkCreditNoteTab();
        assertApiSuccess("/credit_note/get_credit_notes/2346");
        DashboardManager.log("✅ Step 16 PASSED");
    }

    @Test @Order(17)
    @DisplayName("Step 17 — Debit Note tab")
    void step17_debitNoteTab() {
        section("STEP 17 — Debit Note tab");
        healthCheck.checkDebitNoteTab();
        assertApiSuccess("/debit_note/get_debit_notes/2346");
        DashboardManager.log("✅ Step 17 PASSED");
    }

    @Test @Order(18)
    @DisplayName("Step 18 — Cloud Billing")
    void step18_cloudBilling() {
        section("STEP 18 — Cloud Billing");
        healthCheck.checkCloudBilling();
        assertApiSuccess("/sale_order/cloud_bill_shift/uploaded/logs");
        DashboardManager.log("✅ Step 18 PASSED");
    }

    @Test @Order(19)
    @DisplayName("Step 19 — Process Notes")
    void step19_processNotes() {
        section("STEP 19 — Process Notes");
        healthCheck.checkProcessNotes();
        assertApiSuccess("/process_notes");
        DashboardManager.log("✅ Step 19 PASSED");
    }

    // ================================================================== //
    //  STEPS 20 – 30  (Approvals / Finance / Currency / Sales Orders)
    // ================================================================== //

    @Test @Order(20)
    @DisplayName("Step 20 — Requests / PO Approval  [400 expected — no access]")
    void step20_requests() {
        section("STEP 20 — Requests (PO Approval)");
        healthCheck.checkRequests();
        assertApiSuccessPermissive("/purchase_order/get_purchase_orders/");
        DashboardManager.log("✅ Step 20 PASSED (400 is expected — no module access)");
    }

    @Test @Order(21)
    @DisplayName("Step 21 — Transfer Approval  [400 expected — no access]")
    void step21_transferApproval() {
        section("STEP 21 — Transfer Approval");
        healthCheck.checkTransferApproval();
        assertApiSuccessPermissive("/transfer/get-all-requests");
        DashboardManager.log("✅ Step 21 PASSED (400 is expected — no module access)");
    }

    @Test @Order(22)
    @DisplayName("Step 22 — Refund Approval  [400 expected — no access]")
    void step22_refundApproval() {
        section("STEP 22 — Refund Approval");
        healthCheck.checkRefundApproval();
        assertApiSuccessPermissive("/refund/get_all_requests");
        DashboardManager.log("✅ Step 22 PASSED (400 is expected — no module access)");
    }

    @Test @Order(23)
    @DisplayName("Step 23 — My Escrow (Approved Transfer)")
    void step23_myEscrow() {
        section("STEP 23 — My Escrow");
        healthCheck.checkMyEscrow();
        assertApiSuccess("/user/approved-transfer/all-requests");
        DashboardManager.log("✅ Step 23 PASSED");
    }

    @Test @Order(24)
    @DisplayName("Step 24 — My Escrow > Refund Request tab")
    void step24_myEscrowRefundRequest() {
        section("STEP 24 — My Escrow > Refund Request tab");
        healthCheck.checkMyEscrowRefundRequest();
        assertApiSuccess("/user/escrow-refund/all-requests");
        DashboardManager.log("✅ Step 24 PASSED");
    }

    @Test @Order(25)
    @DisplayName("Step 25 — Finance > Refund to Client")
    void step25_financeRefundToClient() {
        section("STEP 25 — Finance > Refund to Client");
        healthCheck.checkFinanceRefundToClient();
        assertApiSuccess("/refund/get_all_requests");
        DashboardManager.log("✅ Step 25 PASSED");
    }

    @Test @Order(26)
    @DisplayName("Step 26 — Finance > Refund From Vendor tab")
    void step26_financeRefundFromVendor() {
        section("STEP 26 — Finance > Refund From Vendor");
        healthCheck.checkFinanceRefundFromVendor();
        assertApiSuccess("filter_val=from_dn");
        DashboardManager.log("✅ Step 26 PASSED");
    }

    @Test @Order(27)
    @DisplayName("Step 27 — Finance > Escrow Refund tab")
    void step27_financeEscrowRefund() {
        section("STEP 27 — Finance > Escrow Refund");
        healthCheck.checkFinanceEscrowRefund();
        assertApiSuccess("filter_val=from_escrow");
        DashboardManager.log("✅ Step 27 PASSED");
    }

    @Test @Order(28)
    @DisplayName("Step 28 — Finance > Escrow Transfer tab")
    void step28_financeEscrowTransfer() {
        section("STEP 28 — Finance > Escrow Transfer");
        healthCheck.checkFinanceEscrowTransfer();
        assertApiSuccess("action_type=fin_transfer_approve");
        DashboardManager.log("✅ Step 28 PASSED");
    }

    @Test @Order(29)
    @DisplayName("Step 29 — Currency Management")
    void step29_currencyManagement() {
        section("STEP 29 — Currency Management");
        healthCheck.checkCurrencyManagement();
        assertApiSuccess("/exchange-rate");
        DashboardManager.log("✅ Step 29 PASSED");
    }

    @Test @Order(30)
    @DisplayName("Step 30 — Sales Orders")
    void step30_salesOrders() {
        section("STEP 30 — Sales Orders");
        healthCheck.checkSalesOrders();
        assertApiSuccess("/fin/get_sales_orders");
        DashboardManager.log("✅ Step 30 PASSED");
    }

    // ================================================================== //
    //  STEPS 31 – 40  (Finance sub-tabs / Reports)
    // ================================================================== //

    @Test @Order(31)
    @DisplayName("Step 31 — Sales Orders > Deals Sales Order tab")
    void step31_dealsSalesOrderTab() {
        section("STEP 31 — Deals Sales Order tab");
        healthCheck.checkDealsSalesOrderTab();
        assertApiSuccess("/deal/fin/get_sales_orders");
        DashboardManager.log("✅ Step 31 PASSED");
    }

    @Test @Order(32)
    @DisplayName("Step 32 — Doc Requests (PMT)")
    void step32_docRequests() {
        section("STEP 32 — Doc Requests");
        healthCheck.checkDocRequests();
        assertApiSuccess("/fin/get_po_under_doc_verification");
        DashboardManager.log("✅ Step 32 PASSED");
    }

    @Test @Order(33)
    @DisplayName("Step 33 — Doc Requests > Deals Document Verification tab")
    void step33_dealDocVerificationTab() {
        section("STEP 33 — Deals Document Verification tab");
        healthCheck.checkDealDocVerificationTab();
        assertApiSuccess("/fin/get_deal_under_doc_verification");
        DashboardManager.log("✅ Step 33 PASSED");
    }

    @Test @Order(34)
    @DisplayName("Step 34 — Purchase Orders (Finance)")
    void step34_purchaseOrders() {
        section("STEP 34 — Purchase Orders");
        healthCheck.checkPurchaseOrders();
        assertApiSuccess("/fin/get_all_purchase_orders");
        DashboardManager.log("✅ Step 34 PASSED");
    }

    @Test @Order(35)
    @DisplayName("Step 35 — Credit Notes (Finance)")
    void step35_creditNotesFinance() {
        section("STEP 35 — Credit Notes");
        healthCheck.checkCreditNotesFinance();
        assertApiSuccess("/fin/credit-notes");
        DashboardManager.log("✅ Step 35 PASSED");
    }

    @Test @Order(36)
    @DisplayName("Step 36 — Debit Notes (Finance)")
    void step36_debitNotesFinance() {
        section("STEP 36 — Debit Notes");
        healthCheck.checkDebitNotesFinance();
        assertApiSuccess("/fin/debit-notes");
        DashboardManager.log("✅ Step 36 PASSED");
    }

    @Test @Order(37)
    @DisplayName("Step 37 — Escrow Finance (Approved Transfer)")
    void step37_escrowFinance() {
        section("STEP 37 — Escrow Finance");
        healthCheck.checkEscrowFinance();
        assertApiSuccess("/fin/approved-transfer/all-requests");
        DashboardManager.log("✅ Step 37 PASSED");
    }

    @Test @Order(38)
    @DisplayName("Step 38 — Escrow Finance > Refund Request tab")
    void step38_escrowFinanceRefundRequest() {
        section("STEP 38 — Escrow Finance > Refund Request");
        healthCheck.checkEscrowFinanceRefundRequest();
        assertApiSuccess("/fin/escrow-refund/all-requests");
        DashboardManager.log("✅ Step 38 PASSED");
    }

    @Test @Order(39)
    @DisplayName("Step 39 — Vendor Approval")
    void step39_vendorApproval() {
        section("STEP 39 — Vendor Approval");
        healthCheck.checkVendorApproval();
        assertApiSuccess("/vendors");
        DashboardManager.log("✅ Step 39 PASSED");
    }

    @Test @Order(40)
    @DisplayName("Step 40 — Reports  [nav check only — no module access]")
    void step40_reports() {
        section("STEP 40 — Reports");
        healthCheck.checkReports();
        assertApiSuccess("/user/nav_perms");
        DashboardManager.log("✅ Step 40 PASSED (nav check only — report_module_access=false)");
    }

    // ================================================================== //
    //  STEPS 41 – 46  (Services & Goods / Vendors — FINAL)
    // ================================================================== //

    @Test @Order(41)
    @DisplayName("Step 41 — Services & Goods > Services")
    void step41_servicesPage() {
        section("STEP 41 — Services");
        healthCheck.checkServicesPage();
        assertApiSuccess("/service/get_all_services?product_type=service");
        DashboardManager.log("✅ Step 41 PASSED");
    }

    @Test @Order(42)
    @DisplayName("Step 42 — Services & Goods > Goods")
    void step42_goodsPage() {
        section("STEP 42 — Goods");
        healthCheck.checkGoodsPage();
        assertApiSuccess("/service/get_all_services?product_type=goods");
        DashboardManager.log("✅ Step 42 PASSED");
    }

    @Test @Order(43)
    @DisplayName("Step 43 — Services & Goods > Industry")
    void step43_industryPage() {
        section("STEP 43 — Industry");
        healthCheck.checkIndustryPage();
        assertApiSuccess("/industry/get_all_industries");
        DashboardManager.log("✅ Step 43 PASSED");
    }

    @Test @Order(44)
    @DisplayName("Step 44 — Services & Goods > Category  [nav check only]")
    void step44_categoryPage() {
        section("STEP 44 — Category");
        healthCheck.checkCategoryPage();
        assertApiSuccess("/user/nav_perms");
        DashboardManager.log("✅ Step 44 PASSED (nav check only)");
    }

    @Test @Order(45)
    @DisplayName("Step 45 — Vendors > All")
    void step45_vendorsAll() {
        section("STEP 45 — Vendors All");
        healthCheck.checkVendorsAll();
        assertApiSuccess("/vendors?page=1");
        DashboardManager.log("✅ Step 45 PASSED");
    }

    @Test @Order(46)
    @DisplayName("Step 46 — Vendors > Shortlisted")
    void step46_vendorsShortlisted() {
        section("STEP 46 — Vendors Shortlisted");
        healthCheck.checkVendorsShortlisted();
        assertApiSuccess("/deal/get_all_supply_deals");
        DashboardManager.log("✅ Step 46 PASSED");
    }

    // -----------------------------------------------------------------------
    // Assertion helpers
    // -----------------------------------------------------------------------

    /**
     * Asserts that at least one captured result matching {@code urlFragment}
     * returned HTTP 2xx. Timing is informational — never fails on slow APIs.
     */
    private void assertApiSuccess(String urlFragment) {
        List<ApiResult> all = healthCheck.getResults();

        List<ApiResult> matching = all.stream()
                .filter(r -> r.url.contains(urlFragment))
                .collect(Collectors.toList());

        if (matching.isEmpty()) {
            String msg = "No API call captured for URL containing: " + urlFragment
                    + "\nAll captured URLs:\n"
                    + all.stream().map(r -> "  " + r.url).collect(Collectors.joining("\n"));
            DashboardManager.log("❌ ASSERT FAILED — " + msg);
            fail(msg);
        }

        boolean anySuccess = matching.stream().anyMatch(ApiResult::isSuccess);
        if (!anySuccess) {
            String errors = matching.stream()
                    .map(r -> String.format("  HTTP %d  %dms  %s",
                            r.httpStatus, r.elapsedMs, r.url))
                    .collect(Collectors.joining("\n"));
            String msg = "All API calls for [" + urlFragment + "] failed:\n" + errors;
            DashboardManager.log("❌ ASSERT FAILED — " + msg);
            fail(msg);
        }
    }

    /**
     * Permissive variant — verifies the request fired at all.
     * Used for pages where a 400 is EXPECTED (permission boundary).
     * Logs the non-2xx as an info note, never fails the build.
     */
    private void assertApiSuccessPermissive(String urlFragment) {
        List<ApiResult> all = healthCheck.getResults();
        boolean found = all.stream().anyMatch(r -> r.url.contains(urlFragment));
        if (!found) {
            String msg = "No API call captured for URL containing: " + urlFragment
                    + " — page may not have loaded at all.";
            DashboardManager.log("❌ ASSERT FAILED — " + msg);
            fail(msg);
        }
        all.stream()
                .filter(r -> r.url.contains(urlFragment) && !r.isSuccess())
                .forEach(r -> DashboardManager.log(
                        "⚠️ Expected non-2xx: HTTP " + r.httpStatus + "  " + r.url));
    }

    // -----------------------------------------------------------------------
    // Console / report helpers
    // -----------------------------------------------------------------------

    private static void section(String title) {
        String line = "═".repeat(64);
        DashboardManager.log("\n" + line);
        DashboardManager.log("  " + title);
        DashboardManager.log(line);
    }

    /**
     * Logs the full API timing summary to DashboardManager so it appears
     * in both the console and the ExtentReports HTML.
     */
    private static void logApiSummary() {
        List<ApiResult> all = healthCheck.getResults();

        long ok         = all.stream().filter(r -> r.elapsedMs >= 0 && r.elapsedMs <= 500).count();
        long acceptable = all.stream().filter(r -> r.elapsedMs > 500 && r.elapsedMs <= 1000).count();
        long slow       = all.stream().filter(r -> r.elapsedMs > 1000).count();
        long failed     = all.stream().filter(r -> !r.isSuccess()).count();

        DashboardManager.log("\n" + "█".repeat(64));
        DashboardManager.log("  API TIMING SUMMARY");
        DashboardManager.log("█".repeat(64));
        DashboardManager.log(String.format("  Total API calls captured  : %d", all.size()));
        DashboardManager.log(String.format("  ✅ OK        (<=500 ms)   : %d", ok));
        DashboardManager.log(String.format("  ⚠️  Acceptable (<=1000ms)  : %d", acceptable));
        DashboardManager.log(String.format("  🐢 Slow       (>1000ms)   : %d  (timing warning — not a failure)", slow));
        DashboardManager.log(String.format("  ❗ HTTP errors (non-2xx)   : %d  (400 on approval pages is expected)", failed));

        if (slow > 0) {
            DashboardManager.log("\n  SLOW CALLS (>1000ms) — investigate these:");
            all.stream()
                    .filter(r -> r.elapsedMs > 1000)
                    .sorted((a, b) -> Long.compare(b.elapsedMs, a.elapsedMs))
                    .forEach(r -> DashboardManager.log(String.format(
                            "  🐢 %5dms  %-6s  %s", r.elapsedMs, r.method,
                            r.url.replace("https://api-embcrm.emb.global/api/v1", ""))));
        }
        DashboardManager.log("█".repeat(64));
    }
}