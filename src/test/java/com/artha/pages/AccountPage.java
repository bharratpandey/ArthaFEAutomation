package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import com.artha.utils.DashboardManager;

import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountPage {
    private final Page page;

    public AccountPage(Page page) {
        this.page = page;
    }

    // ---------- 0) Login ----------
    public void login(String email, String password) {
        page.navigate("https://dev-kam-v2.emb.global/login");
        page.waitForLoadState();
        page.locator("input[name=\"email\"]").fill(email);
        page.locator("input[name=\"password\"]").fill(password);
        page.locator("button:has-text(\"Sign In\")").click();
        // Wait for account menu row to appear after login
        page.waitForSelector("div.cursor-pointer:has-text(\"account\")", new Page.WaitForSelectorOptions().setTimeout(15000));
    }

    // 1) Account module click
    public void openAccountModule() {
        page.locator("div.flex.justify-between.items-center.cursor-pointer:has-text(\"account\")").click();
        page.waitForSelector("button:has-text(\"Create Account\")", new Page.WaitForSelectorOptions().setTimeout(10000));
    }

    // 2) Create Account CTA
    public void clickCreateAccount() {
        page.locator("button:has-text(\"Create Account\")").click();
        page.waitForSelector("input[name=\"account_name\"]", new Page.WaitForSelectorOptions().setTimeout(10000));
    }

    // --- Robust combobox: waits for suggestion items before picking exact/closest match ---
    private void selectComboboxAndPickExact(String inputSelector, String desiredValue) {
        Locator input = page.locator(inputSelector);
        if (input.count() == 0) return;

        input.click();
        input.fill(desiredValue == null ? "" : desiredValue);
        long deadline = System.currentTimeMillis() + 3000;

        Locator suggestions = page.locator(
                "div[role='listbox'] [role='option'], " +
                        "ul[role='listbox'] li, " +
                        "div[role='presentation'] [role='option'], " +
                        "div.MuiAutocomplete-popper li, " +
                        "div[role='presentation'] div[role='option']"
        );

        while (System.currentTimeMillis() < deadline && suggestions.count() == 0) {
            page.waitForTimeout(150);
        }

        int count = suggestions.count();
        if (count > 0) {
            String desiredNorm = desiredValue == null ? "" : desiredValue.trim().toLowerCase();
            // 1) exact case-insensitive match first
            for (int i = 0; i < count; i++) {
                String text = suggestions.nth(i).textContent();
                if (text == null) text = "";
                String txtNorm = text.trim().replaceAll("\\s+", " ").toLowerCase();
                if (txtNorm.equals(desiredNorm)) {
                    suggestions.nth(i).click();
                    page.waitForTimeout(150);
                    return;
                }
            }
            // 2) containing match next
            for (int i = 0; i < count; i++) {
                String text = suggestions.nth(i).textContent();
                if (text == null) text = "";
                String txtNorm = text.trim().toLowerCase();
                if (desiredNorm.length() > 0 && txtNorm.contains(desiredNorm)) {
                    suggestions.nth(i).click();
                    page.waitForTimeout(150);
                    return;
                }
            }
        }

        // fallback keyboard navigation
        page.keyboard().press("ArrowDown");
        page.keyboard().press("Enter");
    }

    // Helper: click a button-based dropdown and choose visible option text if present
    private void chooseFromRenderedList(String optionText) {
        if (page.locator("text=\"" + optionText + "\"").count() > 0) {
            page.locator("text=\"" + optionText + "\"").first().click();
        } else {
            page.keyboard().press("ArrowDown");
            page.keyboard().press("Enter");
        }
    }

    /**
     * Helper: pick currency based on the country & provided currency text
     */
    /**
     * Updated Helper: Picks currency regardless of country or account type.
     * Manual currency input now takes absolute priority.
     */
    private void selectCurrencyForCountry(String country, String currency) {
        Locator currencyInput = page.locator("input#curreny");
        if (currencyInput.count() == 0) {
            return;
        }

        // 1) Click and clear to ensure a fresh search
        currencyInput.click();
        currencyInput.fill("");
        page.waitForTimeout(150);

        String toType = null;

        // 2) PRIORITY: If user provided a currency, use it regardless of account type
        if (currency != null && !currency.trim().isEmpty()) {
            toType = currency;
        }
        // 3) FALLBACK: Only auto-default if no manual currency was provided
        else if (country != null && "United States".equalsIgnoreCase(country.trim())) {
            toType = "United States Dollar";
        }
        else if (country != null && "India".equalsIgnoreCase(country.trim())) {
            // Optional: specify INR as a default for India if nothing is provided
            toType = "Indian Rupee";
        }

        // 4) Execute the selection
        if (toType != null) {
            selectComboboxAndPickExact("input#curreny", toType);
            page.waitForTimeout(200);
        }
    }

    /**
     * Helper: Auto-fill postal code based on selected country
     * - India -> 122105
     * - United States -> 75211
     * - otherwise -> leave empty or use fallbackPostal
     */
    private void autoFillPostalCode(String country, String fallbackPostal) {
        Locator postal = page.locator("input[name='postal_code']");
        if (postal.count() == 0) return;     // field not found → skip

        String c = (country == null) ? "" : country.trim();

        if (c.isEmpty()) {
            String[] countryInputs = new String[] {
                    "input#country",
                    "input[id='country']",
                    "input#search_map_contact",
                    "input[placeholder='Country']",
                    "input[aria-label='Country']",
                    "input.MuiAutocomplete-input",
                    "input[role='combobox']"
            };

            long deadline = System.currentTimeMillis() + 2000; // wait up to 2s for the country input to update
            outer:
            while (System.currentTimeMillis() < deadline) {
                for (String sel : countryInputs) {
                    Locator cLoc = page.locator(sel);
                    if (cLoc.count() > 0) {
                        try {
                            String val = cLoc.first().inputValue();
                            if (val != null) val = val.trim();
                            if (val != null && !val.isEmpty()) {
                                c = val;
                                break outer;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                page.waitForTimeout(100);
            }
        }

        if (c == null || c.isEmpty()) {
            if (fallbackPostal != null && !fallbackPostal.trim().isEmpty()) {
                postal.fill(fallbackPostal.trim());
            }
            return;
        }

        String cLower = c.toLowerCase();

        if (cLower.contains("india")) {
            postal.fill("122105");
            return;
        }

        if (cLower.contains("united states") || cLower.contains("united states of america") || cLower.contains("usa")) {
            postal.fill("75211");
            return;
        }

        if (fallbackPostal != null && !fallbackPostal.trim().isEmpty()) {
            postal.fill(fallbackPostal.trim());
        }
    }

    /**
     * Wait for either the "contact exists" or "contact not found" toast after entering email.
     * Returns Boolean.TRUE if contact exists, Boolean.FALSE if not found, or null if timed out/no decision.
     */
    private Boolean waitForContactEmailToast(int timeoutMs) {
        final String existsMsg = "A contact with this email already exists. We've linked their details with this account and Primary SPOC is added";
        final String notFoundMsg = "Contact details not found, please add Primary SPOC manually";

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (page.locator("text=\"" + existsMsg + "\"").count() > 0) {
                return Boolean.TRUE;
            }
            if (page.locator("text=\"" + notFoundMsg + "\"").count() > 0) {
                return Boolean.FALSE;
            }
            page.waitForTimeout(250);
        }
        return null; // neither appeared in time
    }

    // Helper: wait up to timeoutMs ms for locator to be present and enabled (not disabled)
    private boolean waitForEnabled(Locator locator, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (locator.count() > 0) {
                try {
                    if (!locator.first().isDisabled()) return true;
                } catch (Exception ignored) {
                    // element may be detached; loop and retry
                }
            }
            page.waitForTimeout(150);
        }
        return false;
    }

    /**
     * Tries several selectors and attempts to fill GSTIN and upload GST file with retries.
     * This method will not throw — it will attempt to fill & upload several times and log issues.
     */
    private void fillGstinAndUploadWithRetry(String gstin, String gstFilePath) {
        if ((gstin == null || gstin.trim().isEmpty()) && (gstFilePath == null || gstFilePath.trim().isEmpty())) {
            // nothing to do
            return;
        }

        // Candidate GSTIN selectors (expandable)
        String[] gstinSelectors = new String[] {
                "input[name='gstin']",
                "input[placeholder='Enter GSTIN']",
                "input[id*='gstin']",
                "input[name*='gstin']"
        };

        // Candidate GST file selectors
        String[] gstFileSelectors = new String[] {
                "input[name='company_gstin_file']",
                "input[accept='.pdf'][name*='gst']",
                "input[type='file'][name*='gst']"
        };

        boolean gstinFilled = false;
        boolean gstUploaded = false;

        int attempts = 6;
        for (int attempt = 1; attempt <= attempts && (!gstinFilled || (gstFilePath != null && !gstFilePath.isEmpty() && !gstUploaded)); attempt++) {
            DashboardManager.log("DEBUG: GST attempt " + attempt + " of " + attempts);

            // Try to find gstin input
            Locator gstinInput = null;
            for (String sel : gstinSelectors) {
                Locator l = page.locator(sel);
                if (l.count() > 0) {
                    gstinInput = l;
                    break;
                }
            }

            if (!gstinFilled && gstinInput != null && gstinInput.count() > 0) {
                // wait up to 2s for enabled state on each attempt
                boolean ready = waitForEnabled(gstinInput, 2000);
                if (ready) {
                    try {
                        gstinInput.first().fill(gstin);
                        page.waitForTimeout(150);
                        gstinFilled = true;
                        DashboardManager.log("DEBUG: GSTIN filled.");
                    } catch (Exception e) {
                        DashboardManager.log("WARN: GSTIN fill failed on attempt " + attempt + " -> " + e.getMessage());
                    }
                } else {
                    DashboardManager.log("DEBUG: GSTIN input found but not enabled yet (attempt " + attempt + ").");
                }
            } else if (!gstinFilled && gstinInput == null) {
                DashboardManager.log("DEBUG: GSTIN input not found on attempt " + attempt + ".");
            }

            // Try upload (only if a path provided)
            if (gstFilePath != null && !gstFilePath.isEmpty() && !gstUploaded) {
                Locator gstFileInput = null;
                for (String sel : gstFileSelectors) {
                    Locator l = page.locator(sel);
                    if (l.count() > 0) {
                        gstFileInput = l;
                        break;
                    }
                }

                if (gstFileInput != null && gstFileInput.count() > 0) {
                    // wait briefly for presence
                    long dl = System.currentTimeMillis() + 2000;
                    while (System.currentTimeMillis() < dl && gstFileInput.count() == 0) page.waitForTimeout(100);

                    try {
                        Path g = Paths.get(gstFilePath);
                        gstFileInput.first().setInputFiles(g);
                        gstUploaded = true;
                        DashboardManager.log("DEBUG: GST file uploaded.");
                    } catch (Exception e) {
                        DashboardManager.log("WARN: GST file upload failed on attempt " + attempt + " -> " + e.getMessage());
                    }
                } else {
                    DashboardManager.log("DEBUG: GST file input not found on attempt " + attempt + ".");
                }
            }

            if (gstinFilled && (gstFilePath == null || gstFilePath.isEmpty() || gstUploaded)) break;

            // small wait between attempts
            page.waitForTimeout(700);
        }

        if (!gstinFilled) {
            DashboardManager.log("WARN: GSTIN was not filled after retries.");
        }
        if (gstFilePath != null && !gstFilePath.isEmpty() && !gstUploaded) {
            DashboardManager.log("WARN: GST file not uploaded after retries.");
        }
    }

    /**
     * Fill the account form following your numbered execution order.
     * IMPORTANT: Email is filled first to trigger the toast behavior early (and we block until that toast appears or timeout).
     */
    public void fillAccountFormOrdered(
            String accountName,               // 3
            String registeredBusinessName,    // 4
            String accountHolderName,         // 5
            String accountType,               // 6: Registered/Unregistered/Overseas/SEZ
            String phoneNumber,               // 7
            String companyPan,                // 8
            String panFilePath,               // 9
            String contactEmail,              // 10  <-- filled first
            String industry,                  // 11
            String country,                   // 12 (for Overseas -> "United States")
            String postalCode,                // 13 (fallback; auto-fill preferred)
            String completeAddress,           // 14
            String currency,                  // 15 (used only for non-India, non-US fallbacks)
            String gstin,                     // 16 (GST in SEZ/Registered)
            String gstFilePath,               // 17
            String firstName,                 // 18 (note: after 3s wait)
            String lastName                   // 19
    ) {
        // --- 0) Fill email FIRST and wait for toast (block up to 30s) ---
        Locator emailInput = page.locator("input[type='email']");
        if (emailInput.count() == 0) {
            emailInput = page.locator("input[name*='email']");
        }
        Boolean contactExists = null;
        if (emailInput.count() > 0) {
            emailInput.first().fill(contactEmail);

            // Blur email by focusing account_name (preferred) or pressing Tab
            Locator accNameInput = page.locator("input[name='account_name']");
            if (accNameInput.count() > 0) {
                try {
                    accNameInput.first().click();
                } catch (Exception e) {
                    page.keyboard().press("Tab");
                }
            } else {
                page.keyboard().press("Tab");
            }

            // WAIT: block here until one of the toasts appears or timeout (30s)
            contactExists = waitForContactEmailToast(30000); // 30s
            DashboardManager.log("DEBUG: contactExists = " + contactExists);
        }

        // 3) Account name
        page.locator("input[name=\"account_name\"]").fill(accountName);

        // 4) Registered Business name
        page.locator("input[name=\"registered_business_name\"]").fill(registeredBusinessName);

        // 5) Bank Account Holder Name
        page.locator("input[name=\"account_holder_name\"]").fill(accountHolderName);

        // 6) Account type (button combobox) - select required type
        Locator acctTypeBtn = page.locator("button[name=\"account_type\"]");
        String selectedAccountTypeLabel = "";
        if (acctTypeBtn.count() > 0) {
            acctTypeBtn.click();
            page.waitForTimeout(500); // larger wait to let UI re-render fields
            chooseFromRenderedList(accountType);
            page.waitForTimeout(400);

            // Attempt to read displayed label
            try {
                Locator span = page.locator("button[name='account_type'] span");
                if (span.count() > 0) {
                    String selText = span.first().textContent();
                    if (selText != null) selectedAccountTypeLabel = selText.trim();
                    DashboardManager.log("DEBUG: selected account_type label = " + selectedAccountTypeLabel);
                }
            } catch (Exception ignored) {}
        }

        // Determine flags
        boolean isOverseas = false;
        boolean isRegisteredOrSez = false;
        if (selectedAccountTypeLabel != null && !selectedAccountTypeLabel.isEmpty()) {
            String lbl = selectedAccountTypeLabel.toLowerCase();
            if (lbl.contains("overseas")) isOverseas = true;
            if (lbl.contains("registered") || lbl.contains("special economic") || lbl.contains("sez")) isRegisteredOrSez = true;
        } else {
            // fallback to param
            isOverseas = accountType != null && accountType.equalsIgnoreCase("Overseas");
            isRegisteredOrSez = accountType != null && (accountType.equalsIgnoreCase("Registered") || accountType.equalsIgnoreCase("SEZ"));
        }

        // 7) Phone number
        page.locator("input[name=\"phone_number\"]").fill(phoneNumber);

        if (isRegisteredOrSez) {
            DashboardManager.log("DEBUG: Treating account type as Registered/SEZ -> will fill GSTIN and upload file.");
        }

        // 8) Company PAN (text) - only if NOT Overseas
        if (!isOverseas) {
            page.locator("input[name=\"company_pan\"]").fill(companyPan);
        }

        // 9) Company PAN file upload - only if NOT Overseas
        if (!isOverseas && panFilePath != null && !panFilePath.isEmpty() && page.locator("input[name=\"account_pan_file\"]").count() > 0) {
            Path p = Paths.get(panFilePath);
            page.locator("input[name=\"account_pan_file\"]").first().setInputFiles(p);
        }

        // 11) Industry (searchable)
        selectComboboxAndPickExact("input#industry", industry);

        // 12) Country - if Overseas select United States; otherwise leave blank
        // 12) Country - Use the provided country parameter instead of hardcoded "United States"
        if (isOverseas) {
            String[] countryInputs = new String[] { "input#country", "input[id='country']", "input[placeholder='Country']", "input[aria-label='Country']" };
            boolean picked = false;
            for (String csel : countryInputs) {
                if (page.locator(csel).count() > 0) {
                    // Replaced hardcoded "United States" with 'country' variable
                    selectComboboxAndPickExact(csel, country);
                    picked = true;
                    break;
                }
            }
            if (!picked) {
                selectComboboxAndPickExact("input#country", country);
            }
            page.waitForTimeout(300);
        }

        // 13) Postal / Pin - auto-fill by country with fallback to provided postalCode
// Changed: removed the ternary that forced "United States"
        autoFillPostalCode(country, postalCode);

// 14) Complete address
        page.locator("input[name=\"complete_address\"]").fill(completeAddress);

// 15) Currency (searchable)
// Changed: removed the ternary that forced "United States"
        selectCurrencyForCountry(country, currency);

        // 16 & 17) GSTIN and GST upload - only for Registered or SEZ (skip for Overseas & Unregistered)
        if (isRegisteredOrSez) {
            fillGstinAndUploadWithRetry(gstin, gstFilePath);
        }

        // 3 second wait as requested before first/last name
        page.waitForTimeout(3000);

        // 18 & 19) First & Last name - fill only if present and enabled, and only if contact wasn't auto-linked
        Locator firstNameLoc = page.locator("input[name=\"contact_first_name\"]");
        Locator lastNameLoc = page.locator("input[name=\"contact_last_name\"]");

        long namesDeadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < namesDeadline && (firstNameLoc.count() == 0 || lastNameLoc.count() == 0)) {
            page.waitForTimeout(100);
        }

        if (!Boolean.TRUE.equals(contactExists)) {
            if (firstNameLoc.count() > 0) {
                try {
                    if (!firstNameLoc.first().isDisabled()) {
                        firstNameLoc.first().fill(firstName);
                    }
                } catch (Exception e) {
                    if (firstNameLoc.count() > 0) firstNameLoc.first().fill(firstName);
                }
            }
            if (lastNameLoc.count() > 0) {
                try {
                    if (!lastNameLoc.first().isDisabled()) {
                        lastNameLoc.first().fill(lastName);
                    }
                } catch (Exception e) {
                    if (lastNameLoc.count() > 0) lastNameLoc.first().fill(lastName);
                }
            }
        } else {
            DashboardManager.log("DEBUG: contact existed according to toast — skipping name fills.");
        }

        // Form filled in requested order
    }

    // 20) Submit Create Account (updated to handle confirmation modal more robustly)
    public void submitCreateAccount() {
        Locator submitBtn = page.locator("button[type='submit']:has-text(\"Create Account\")");
        if (submitBtn.count() > 0) submitBtn.first().click();
        else if (page.locator("button:has-text(\"Create Account\")").count() > 0) page.locator("button:has-text(\"Create Account\")").first().click();

        boolean dialogShown = false;
        try {
            page.waitForSelector("div[role='dialog']:has-text(\"Confirm Creation\")", new Page.WaitForSelectorOptions().setTimeout(15000));
            dialogShown = true;
        } catch (com.microsoft.playwright.TimeoutError e) {
            // dialog didn't appear — proceed
            DashboardManager.log("DEBUG: confirm dialog did not appear within timeout, continuing.");
        }

        if (dialogShown) {
            Locator yesCreateBtn = page.locator("div[role='dialog'] button:has-text(\"Yes, Create\")");
            if (yesCreateBtn.count() > 0) {
                yesCreateBtn.first().click();
            } else if (page.locator("button:has-text(\"Yes, Create\")").count() > 0) {
                page.locator("button:has-text(\"Yes, Create\")").first().click();
            }
        }

        page.waitForTimeout(2000);
    }

    // Utility wait for success message (adjust expected text as per app)
    public boolean waitForSuccessToast(String text, int timeoutMs) {
        try {
            page.waitForSelector("text=\"" + text + "\"", new Page.WaitForSelectorOptions().setTimeout(timeoutMs));
            return true;
        } catch (com.microsoft.playwright.TimeoutError e) {
            return false;
        }
    }
}
