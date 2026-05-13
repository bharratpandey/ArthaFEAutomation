package com.artha.tests;

import com.artha.pages.AccountPage;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Locale;
import java.util.Random;
import com.artha.utils.DashboardManager;

public class AccountTest {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;

    @BeforeAll
    static void setup() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterAll
    static void tearDown() {
        if (context != null) context.close();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test
    public void createAccountFlowExactOrder() throws IOException {
        AccountPage acct = new AccountPage(page);

        // LOGIN (given)
        String loginEmail = "Bharat.pandey@emb.global";
        String loginPassword = "Bharat@123";
        acct.login(loginEmail, loginPassword);

        // 1) Account module click
        acct.openAccountModule();

        // 2) Create Account CTA
        acct.clickCreateAccount();

        // Prepare unique data
        String ts = String.valueOf(Instant.now().toEpochMilli());
        String accountName = "AutoAcc-" + ts;                       // 3
        String regBusiness = "AutoBiz-" + ts;                       // 4
        String accountHolder = "Holder-" + ts;                      // 5
        String accountType = "Overseas"; //  Registered,Overseas, Unregistered, Special Economic Zone (SEZ)                     // 6 -> Registered (GST fields enabled)
        String phone = "7982170497";                                // 7
        String pan = generatePanLike();                             // 8
        Path tmp = Files.createTempDirectory("acct_test_files");
        Path panFile = tmp.resolve("pan_demo.pdf");
        Path gstFile = tmp.resolve("gst_demo.pdf");
        Files.write(panFile, ("PAN demo " + ts).getBytes());
        Files.write(gstFile, ("GST demo " + ts).getBytes());

        String contactEmail = "autotest." + ts + "@yopmail.com";    // 10
        String industry = "Manufacturing";                          // 11
        String country = "United Arab Emirates";   //India,United States ,United Arab Emirates,India                               // 12 (for Registered we will leave country empty per your last message)
        String postal = "00000";                                   // 13
        String address = "Plot No. 17, Phase-4, Maruti Udyog, Sector 18, Gurugram, HR"; // 14
        String currency = "United Arab Emirates Dirham"; //United Arab Emirates Dirham,Saudi Arabian Riyal,Euro, Indian Rupee,Canadian Dollar,Israeli New Shekel,Singapore Dollar,Singapore Dollar,Singapore Dollar,Australian Dollar,United States Dollar                          // 15
        String gstin = generateValidGstin();// 16

        // First/Last names will be filled after 3 sec wait inside page object
        String firstNameRaw = "AutoF" + ts;
        String firstName = firstNameRaw.length() > 20 ? firstNameRaw.substring(0, 20) : firstNameRaw; // 18
        String lastName = "Tester";                                 // 19

        // Fill the form in exact numbered order:
        acct.fillAccountFormOrdered(
                accountName,
                regBusiness,
                accountHolder,
                accountType,
                phone,
                pan,
                panFile.toAbsolutePath().toString(),
                contactEmail,
                industry,
                country,   // FIXED: Replaced "" with country variable
                postal,    // FIXED: Uses the postal variable
                address,
                currency,  // FIXED: Uses the currency variable
                gstin,
                gstFile.toAbsolutePath().toString(),
                firstName,
                lastName
        );

        // 20) Submit
        acct.submitCreateAccount();

        // Wait for success toast (adjust text if your app uses different message)
        boolean ok = acct.waitForSuccessToast("Account has been created successfully.", 10000);
        Assertions.assertTrue(ok, "Expected success toast did not appear - update expected text or selector.");
    }

    // Helper: PAN (ABCDE1234F)
    private static String generatePanLike() {
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append((char) ('A' + rnd.nextInt(26)));
        for (int i = 0; i < 4; i++) sb.append(rnd.nextInt(10));
        sb.append((char) ('A' + rnd.nextInt(26)));
        return sb.toString();
    }

    // Helper: GSTIN-like (not fully validated)
    private static String generateValidGstin() {
        Random rnd = new Random();

        // 1) State Code (01–35) — choose any valid 2-digit code
        int state = 1 + rnd.nextInt(35);
        String stateCode = String.format("%02d", state);

        // 2) PAN: 5 letters + 4 digits + 1 letter
        StringBuilder pan = new StringBuilder();
        for (int i = 0; i < 5; i++) pan.append((char) ('A' + rnd.nextInt(26)));
        for (int i = 0; i < 4; i++) pan.append(rnd.nextInt(10));
        pan.append((char) ('A' + rnd.nextInt(26)));

        // 3) Entity Code (1–9)
        int entityCode = 1 + rnd.nextInt(9);

        // 4) 'Z' constant
        char z = 'Z';

        // 5) Checksum character — random A–Z (simplified)
        char checksum = (char) ('A' + rnd.nextInt(26));

        // Form final GSTIN
        return (stateCode + pan + entityCode + z + checksum).toUpperCase();
    }

}
