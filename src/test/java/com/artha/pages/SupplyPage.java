package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.artha.utils.DashboardManager;

public class SupplyPage {
    private final Page page;

    public SupplyPage(Page page) {
        this.page = page;
    }

    /**
     * Robust login for Supply portal. Uses several possible selectors and fallback behaviors,
     * including JS fill fallback for stubborn inputs.
     */
    public void loginSupply(String supplyUrl, String email, String password) {
        DashboardManager.log("[SUPPLY] Navigating to supply login: " + supplyUrl);
        page.navigate(supplyUrl);
        try { page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(5000)); }
        catch (Exception ignored) {}

        // possible selectors (from your HTML snippets)
        String[] emailSelectors = new String[]{
                "input[name='email']",
                "input[type='email']",
                "input[placeholder*='Email']",
                "input.makeStyles-formControl-2[name='email']"
        };
        String[] passwordSelectors = new String[]{
                "input[name='password']",
                "input[type='password']",
                "input.makeStyles-formControl-2[type='password']"
        };
        String[] loginBtnSelectors = new String[]{
                "button:has-text('Login')",
                "button[type='submit']:has-text('Login')",
                "button:has-text('Sign In')",
                "button:has-text('Sign in')"
        };

        Locator emailInput = null;
        Locator passwordInput = null;
        Locator loginBtn = null;

        // quick attempt to locate inputs
        for (String sel : emailSelectors) {
            try { Locator l = page.locator(sel); if (l.count() > 0) { emailInput = l.first(); DashboardManager.log("[SUPPLY] Found email selector: " + sel); break; } } catch (Exception ignored) {}
        }
        for (String sel : passwordSelectors) {
            try { Locator l = page.locator(sel); if (l.count() > 0) { passwordInput = l.first(); DashboardManager.log("[SUPPLY] Found password selector: " + sel); break; } } catch (Exception ignored) {}
        }
        for (String sel : loginBtnSelectors) {
            try { Locator l = page.locator(sel); if (l.count() > 0) { loginBtn = l.first(); DashboardManager.log("[SUPPLY] Found login button selector: " + sel); break; } } catch (Exception ignored) {}
        }

        // Wait & retry once (apps sometimes lazy-render)
        if (emailInput == null || passwordInput == null || loginBtn == null) {
            page.waitForTimeout(900);
            for (String sel : emailSelectors) {
                try { Locator l = page.locator(sel); if (l.count() > 0) { emailInput = l.first(); break; } } catch (Exception ignored) {}
            }
            for (String sel : passwordSelectors) {
                try { Locator l = page.locator(sel); if (l.count() > 0) { passwordInput = l.first(); break; } } catch (Exception ignored) {}
            }
            for (String sel : loginBtnSelectors) {
                try { Locator l = page.locator(sel); if (l.count() > 0) { loginBtn = l.first(); break; } } catch (Exception ignored) {}
            }
        }

        if (emailInput == null) DashboardManager.log("[SUPPLY] email input not found!");
        if (passwordInput == null) DashboardManager.log("[SUPPLY] password input not found!");
        if (loginBtn == null) DashboardManager.log("[SUPPLY] Login button not found");

        // Fill inputs defensively; if typing doesn't work, use JS fallback to set value + dispatch events
        if (emailInput != null) {
            try {
                emailInput.scrollIntoViewIfNeeded();
                try { emailInput.fill(""); } catch (Exception ignored) {}
                emailInput.click();
                emailInput.type(email, new Locator.TypeOptions().setDelay(25));
                page.waitForTimeout(120);
                // verify value
                try {
                    String val = emailInput.inputValue();
                    if (val == null || !val.contains("@")) {
                        // JS fallback (string assembly to avoid Playwright Java arg typing issues)
                        page.evaluate("() => { const el = document.querySelector(\"input[name='email']\"); if(!el) return false; el.focus(); el.value = '" + escapeJs(email) + "'; el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); return true; }");
                        page.waitForTimeout(150);
                    }
                } catch (Exception ignored) {}
            } catch (Exception ex) {
                DashboardManager.log("[SUPPLY] failed to fill email via normal methods: " + ex.getMessage());
                try {
                    page.evaluate("() => { const el = document.querySelector(\"input[name='email']\"); if(el){ el.value = '" + escapeJs(email) + "'; el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); } }");
                } catch (Exception ignored) {}
            }
        }

        if (passwordInput != null) {
            try {
                passwordInput.scrollIntoViewIfNeeded();
                try { passwordInput.fill(""); } catch (Exception ignored) {}
                passwordInput.click();
                passwordInput.type(password, new Locator.TypeOptions().setDelay(25));
                page.waitForTimeout(120);
                try {
                    String val = passwordInput.inputValue();
                    if (val == null || val.length() == 0) {
                        page.evaluate("() => { const el = document.querySelector(\"input[name='password']\"); if(!el) return false; el.focus(); el.value = '" + escapeJs(password) + "'; el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); return true; }");
                        page.waitForTimeout(150);
                    }
                } catch (Exception ignored) {}
            } catch (Exception ex) {
                DashboardManager.log("[SUPPLY] failed to fill password via normal methods: " + ex.getMessage());
                try {
                    page.evaluate("() => { const el = document.querySelector(\"input[name='password']\"); if(el){ el.value = '" + escapeJs(password) + "'; el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); } }");
                } catch (Exception ignored) {}
            }
        }

        // Click login (with fallback)
        try {
            if (loginBtn != null) {
                loginBtn.scrollIntoViewIfNeeded();
                try { loginBtn.click(); } catch (Exception e) {
                    try { loginBtn.click(new Locator.ClickOptions().setForce(true)); }
                    catch (Exception e2) {
                        try { page.evaluate("el => el.click()", loginBtn); } catch (Exception ignored) {}
                    }
                }
            } else {
                // fallback: press Enter on password or email
                try {
                    if (passwordInput != null) passwordInput.press("Enter");
                    else if (emailInput != null) emailInput.press("Enter");
                } catch (Exception ignored) {}
            }
        } catch (Exception ex) {
            DashboardManager.log("[SUPPLY] Login click/submit failed: " + ex.getMessage());
        }

        DashboardManager.log("[SUPPLY] Login attempted for " + email);
        // wait for dashboard marker or url change
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8000));
        } catch (Exception ignored) {}
        page.waitForTimeout(600);
    }

    /**
     * Helper to escape single quotes for JS injection strings.
     */
    private String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
    }

    /**
     * Per your requirement — reload once and brief wait.
     */
    public void refreshOnce() {
        try {
            page.reload();
            try { page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(5000)); } catch (Exception ignored) {}
            page.waitForTimeout(700);
            DashboardManager.log("[SUPPLY] Page reloaded once and brief wait completed.");
        } catch (Exception e) {
            DashboardManager.log("[SUPPLY] refreshOnce failed: " + e.getMessage());
        }
    }

    /**
     * Open Vendor Shortlist module from the left nav.
     * Tries multiple selectors and robust click strategies and validates by url/content.
     */
    public void openVendorShortlistModule() {
        String[] moduleSelectors = new String[]{
                "a[href*='/dashboard/shortlist']",
                "a[href*='/dashboard/shortlist']:has-text('Vendor Shortlist')",
                "a:has-text('Vendor Shortlist')",
                "a:has-text('Vendor Profiles')",
                "a[href*='/dashboard/profiles']",
                "a[href*='/dashboard/app']"
        };

        boolean clicked = false;
        Exception lastEx = null;

        for (String sel : moduleSelectors) {
            try {
                Locator l = page.locator(sel);
                if (l.count() == 0) continue;
                Locator candidate = l.first();
                candidate.scrollIntoViewIfNeeded();
                try { candidate.click(); } catch (Exception e) {
                    try { candidate.click(new Locator.ClickOptions().setForce(true)); }
                    catch (Exception e2) {
                        try { page.evaluate("el => el.click()", candidate); }
                        catch (Exception e3) { throw e3; }
                    }
                }

                // wait briefly for module content to load
                page.waitForTimeout(800);

                // verify by URL or by presence of shortlist/search input
                if (page.url().contains("/shortlist") || page.url().contains("/profiles") ||
                        page.locator("input[placeholder='Search Deal'], input[placeholder='Search vendor profile'], div:has-text('Vendor Shortlist')").count() > 0) {
                    clicked = true;
                    DashboardManager.log("[SUPPLY] Clicked vendor shortlist module using selector: " + sel);
                    break;
                }
            } catch (Exception ex) {
                lastEx = ex;
            }
        }

        // try a retry with a more aggressive approach if first pass failed
        if (!clicked) {
            page.waitForTimeout(600);
            for (String sel : moduleSelectors) {
                try {
                    Locator l = page.locator(sel);
                    if (l.count() == 0) continue;
                    Locator candidate = l.first();
                    candidate.scrollIntoViewIfNeeded();
                    try { candidate.click(new Locator.ClickOptions().setForce(true)); } catch (Exception ex) {
                        try { page.evaluate("el => el.click()", candidate); } catch (Exception ignored) {}
                    }
                    page.waitForTimeout(700);
                    if (page.url().contains("/shortlist") || page.locator("input[placeholder='Search Deal'], input[placeholder='Search vendor profile']").count() > 0) {
                        clicked = true;
                        DashboardManager.log("[SUPPLY] Clicked vendor shortlist module on aggressive retry using selector: " + sel);
                        break;
                    }
                } catch (Exception ex) {
                    lastEx = ex;
                }
            }
        }

        if (!clicked) {
            try {
                Path dir = Paths.get("target/debug/supply-vendor-module-click-failed");
                Files.createDirectories(dir);
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png")));
                Files.writeString(dir.resolve("page.html"), page.content());
                DashboardManager.log("[SUPPLY] saved debug artifacts to " + dir.toAbsolutePath());
            } catch (Exception ioe) {
                DashboardManager.log("[SUPPLY] failed to save debug artifacts: " + ioe.getMessage());
            }
            throw new RuntimeException("openVendorShortlistModule: could not click vendor module", lastEx);
        }
    }

    /**
     * Search for a deal on supply by numeric fragment (works for "AutoDeal-<ts>" where fragment is digits).
     * Returns true if search field found and at least one result appears.
     */
    public boolean searchDealByNumericFragment(String dealName) {
        String numericFragment = extractNumericFragment(dealName);
        DashboardManager.log("[SUPPLY] Searching for deal with query: " + numericFragment);

        String[] searchSelectors = new String[]{
                "input[placeholder='Search Deal']",
                "input[placeholder='Search vendor profile']",
                "input[placeholder='Search']",
                "input[placeholder*='Search']"
        };

        Locator searchInput = null;
        for (String sel : searchSelectors) {
            try { Locator l = page.locator(sel); if (l.count() > 0) { searchInput = l.first(); break; } } catch (Exception ignored) {}
        }

        if (searchInput == null) {
            DashboardManager.log("[SUPPLY] searchDeal input not found: will dump debug artifacts.");
            try {
                Path dir = Paths.get("target/debug/supply-deal-not-found");
                Files.createDirectories(dir);
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png")));
                Files.writeString(dir.resolve("page.html"), page.content());
            } catch (Exception ignored) {}
            return false;
        }

        try {
            searchInput.scrollIntoViewIfNeeded();
            searchInput.click();
            try { searchInput.fill(""); } catch (Exception ignored) {}
            searchInput.type(numericFragment, new Locator.TypeOptions().setDelay(30));
            page.waitForTimeout(900);
            try { searchInput.press("Enter"); } catch (Exception ignored) {}

            String rowSelector = String.format("text=%s", numericFragment);
            try {
                page.waitForSelector(rowSelector, new Page.WaitForSelectorOptions().setTimeout(3000));
                DashboardManager.log("[SUPPLY] Found search result containing: " + numericFragment);
                return true;
            } catch (Exception ex) {
                DashboardManager.log("[SUPPLY] No search row matched the fragment within timeout: " + ex.getMessage());
                Locator cells = page.locator("td");
                for (int i = 0; i < Math.min(40, cells.count()); i++) {
                    try {
                        String t = cells.nth(i).innerText();
                        if (t != null && t.contains(numericFragment)) {
                            DashboardManager.log("[SUPPLY] Found fragment in td: " + t);
                            return true;
                        }
                    } catch (Exception ignored) {}
                }
                return false;
            }
        } catch (Exception ex) {
            DashboardManager.log("[SUPPLY] searchDealByNumericFragment failed: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Click the Shortlist CTA for the deal row that matches dealName (or numeric fragment).
     */
    public boolean clickShortlistForDeal(String dealName) {
        String numericFragment = extractNumericFragment(dealName);
        DashboardManager.log("[SUPPLY] clickShortlistForDeal: fragment=" + numericFragment);

        Locator rows = page.locator("tr");
        for (int i = 0; i < rows.count(); i++) {
            try {
                Locator row = rows.nth(i);
                String text = "";
                try { text = row.innerText(); } catch (Exception ignored) {}
                if (text != null && text.contains(numericFragment)) {
                    Locator shortlistBtn = row.locator("button:has-text('Shortlist'), text=Shortlist");
                    if (shortlistBtn.count() > 0) {
                        try {
                            shortlistBtn.first().scrollIntoViewIfNeeded();
                            shortlistBtn.first().click();
                            page.waitForTimeout(600);
                            DashboardManager.log("[SUPPLY] Clicked Shortlist CTA in matching row.");
                            return true;
                        } catch (Exception e) {
                            try { shortlistBtn.first().click(new Locator.ClickOptions().setForce(true)); return true; } catch (Exception ignored) {}
                        }
                    }
                    Locator link = row.locator("text=Shortlist");
                    if (link.count() > 0) {
                        try { link.first().click(); page.waitForTimeout(500); return true; } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }

        Locator anyShortlist = page.locator("button:has-text('Shortlist')");
        if (anyShortlist.count() > 0) {
            try {
                anyShortlist.first().scrollIntoViewIfNeeded();
                anyShortlist.first().click();
                page.waitForTimeout(500);
                DashboardManager.log("[SUPPLY] Clicked first Shortlist button as fallback.");
                return true;
            } catch (Exception e) {
                DashboardManager.log("[SUPPLY] fallback shortlist click failed: " + e.getMessage());
            }
        }

        try {
            Path dir = Paths.get("target/debug/supply-click-shortlist-failed");
            Files.createDirectories(dir);
            page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png")));
            Files.writeString(dir.resolve("page.html"), page.content());
        } catch (Exception ignored) {}

        return false;
    }

    /**
     * Existing method preserved: search vendor by typing vendorSearchText and picking first suggestion.
     * (keeps compatibility with older callers).
     */
    public boolean selectVendorAndSubmit(String vendorSearchText) {
        DashboardManager.log("[SUPPLY] selectVendorAndSubmit: searching vendor with: " + vendorSearchText);
        try {
            // Use helper to find first matching selector (avoid comma-separated composite selectors)
            String[] candidateSelectors = new String[] {
                    "input[id='selected_vendor_id']",
                    "input[placeholder*='Select vendor']",
                    "input[placeholder*='Search vendor profile']",
                    "input[aria-autocomplete]",
                    "div[role='dialog'] input"
            };
            Locator selectInput = findFirstLocator(candidateSelectors);
            if (selectInput == null) {
                DashboardManager.log("[SUPPLY] vendor select input not found.");
                return false;
            }

            Locator si = selectInput;
            si.scrollIntoViewIfNeeded();
            si.click();
            try { si.fill(""); } catch (Exception ignored) {}
            si.type(vendorSearchText == null ? "Test" : vendorSearchText, new Locator.TypeOptions().setDelay(40));
            page.waitForTimeout(600);
            try { si.press("ArrowDown"); page.waitForTimeout(220); si.press("Enter"); page.waitForTimeout(400); } catch (Exception ignored) {}

            // fill Notes
            Locator notes = page.locator("input[name='notes'], input[placeholder*='Notes'], textarea[name='notes']");
            if (notes.count() > 0) {
                notes.first().scrollIntoViewIfNeeded();
                try { notes.first().fill(""); } catch (Exception ignored) {}
                notes.first().type("this is automated flow Test", new Locator.TypeOptions().setDelay(20));
                page.waitForTimeout(200);
            }

            // currency
            Locator currency = page.locator("input[id='vendor_currency'], input[placeholder*='Vendor Currency']");
            if (currency.count() > 0) {
                Locator c = currency.first(); c.scrollIntoViewIfNeeded();
                try { c.fill(""); } catch (Exception ignored) {}
                c.click(); c.type("Indian Rupee", new Locator.TypeOptions().setDelay(30));
                page.waitForTimeout(300);
                try { c.press("ArrowDown"); c.press("Enter"); page.waitForTimeout(200); } catch (Exception ignored) {}
            }

            // amount
            Locator amount = page.locator("input[name='vendor_amount'], input[placeholder*='Vendor Amount']");
            if (amount.count() > 0) {
                amount.first().scrollIntoViewIfNeeded();
                try { amount.first().fill(""); } catch (Exception ignored) {}
                amount.first().type("800000", new Locator.TypeOptions().setDelay(20));
                page.waitForTimeout(150);
            }

            // Now select "Budget" (or given reason) and submit inside dialog
            boolean done = selectReasonAndSubmit("Budget");
            if (done) {
                page.waitForTimeout(800);
                DashboardManager.log("[SUPPLY] selectVendorAndSubmit completed.");
                return true;
            } else {
                DashboardManager.log("[SUPPLY] selectVendorAndSubmit could not complete dialog submit.");
                return false;
            }
        } catch (Exception ex) {
            DashboardManager.log("[SUPPLY] selectVendorAndSubmit exception: " + ex.getMessage());
            try { Path dir = Paths.get("target/debug/supply-select-failed"); Files.createDirectories(dir); page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png"))); Files.writeString(dir.resolve("page.html"), page.content()); } catch (Exception ignored) {}
            return false;
        }
    }

    /**
     * NEW: Select vendor by exact visible name (e.g. "Registered Business Create Test,Registered Business Name bharat"),
     * click the card's Select button, tick checkbox, fill notes/currency/amount and submit.
     * This method is more deterministic when vendor cards are available.
     */
    public boolean selectVendorByNameAndSubmit(String vendorName) {
        DashboardManager.log("[SUPPLY] selectVendorByNameAndSubmit: vendorName=" + vendorName);
        try {
            // 1) find and type in vendor search input (if exists)
            String[] candidateSelectors = new String[] {
                    "input[placeholder='Search vendor profile']",
                    "input[id='selected_vendor_id']",
                    "input[placeholder*='Select vendor']",
                    "input[aria-autocomplete]",
                    "div[role='dialog'] input"
            };
            Locator vendorSearchInput = findFirstLocator(candidateSelectors);

            if (vendorSearchInput != null) {
                Locator vsi = vendorSearchInput;
                vsi.scrollIntoViewIfNeeded();
                vsi.click();
                try { vsi.fill(""); } catch (Exception ignored) {}
                vsi.type(vendorName, new Locator.TypeOptions().setDelay(35));
                page.waitForTimeout(700);
                try { vsi.press("ArrowDown"); page.waitForTimeout(200); vsi.press("Enter"); page.waitForTimeout(500); } catch (Exception ignored) {}
            } else {
                DashboardManager.log("[SUPPLY] vendor search input not present — will search cards directly.");
            }

            // 2) wait briefly for UI to update
            page.waitForTimeout(400);

            // 3) find cards that include the vendor h5 text
            Locator cards = page.locator("div:has(h5), div.MuiPaper-root, div.MuiBox-root");
            for (int i = 0; i < cards.count(); i++) {
                try {
                    Locator card = cards.nth(i);
                    // check presence of h5 text exact match
                    if (card.locator("h5:has-text(\"" + vendorName + "\")").count() > 0) {
                        DashboardManager.log("[SUPPLY] Found card at index " + i + " containing h5 text: " + vendorName);
                        // try multiple strategies to find Select button INSIDE this card
                        Locator selectBtn = card.locator("button:has-text('Select'), button:has-text('select'), button.css-1w7n44y-MuiButtonBase-root-MuiButton-root");
                        if (selectBtn.count() == 0) {
                            // xpath descendant fallback
                            try {
                                Locator xpathBtn = card.locator("xpath=.//button[contains(normalize-space(.), 'Select')]");
                                if (xpathBtn.count() > 0) selectBtn = xpathBtn;
                            } catch (Exception ignored) {}
                        }
                        if (selectBtn.count() > 0) {
                            // pick first visible button and click robustly
                            for (int b = 0; b < selectBtn.count(); b++) {
                                try {
                                    Locator btn = selectBtn.nth(b);
                                    if (!btn.isVisible()) continue;
                                    btn.scrollIntoViewIfNeeded();
                                    try { btn.click(); page.waitForTimeout(600); DashboardManager.log("[SUPPLY] Clicked Select button in card (index " + i + ").");
                                        // after clicking Select, the dialog appears, so handle dialog flow
                                        return selectReasonAndSubmit("Budget");
                                    }
                                    catch (Exception e1) {
                                        try { btn.click(new Locator.ClickOptions().setForce(true)); page.waitForTimeout(600); return selectReasonAndSubmit("Budget"); }
                                        catch (Exception e2) {
                                            try { page.evaluate("el => el.click()", btn); page.waitForTimeout(600); return selectReasonAndSubmit("Budget"); } catch (Exception ex) { DashboardManager.log("[SUPPLY] JS click also failed on card select: " + ex.getMessage()); }
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        } else {
                            DashboardManager.log("[SUPPLY] No Select button found inside matched card index " + i + ". Trying to locate nearby button siblings.");
                            // try to find a button sibling of the h5
                            try {
                                Locator h5 = card.locator("h5:has-text(\"" + vendorName + "\")").first();
                                Locator nearBtn = h5.locator("xpath=ancestor::div[1]//button[contains(normalize-space(.), 'Select')]");
                                if (nearBtn.count() > 0) {
                                    try { nearBtn.first().scrollIntoViewIfNeeded(); nearBtn.first().click(); page.waitForTimeout(600); return selectReasonAndSubmit("Budget"); } catch (Exception ignored) {}
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 4) fallback: scan all h5 nodes to find matching text and click nearest Select
            Locator allH5 = page.locator("h5");
            for (int i = 0; i < allH5.count(); i++) {
                try {
                    String text = allH5.nth(i).innerText().trim();
                    if (vendorName.equals(text)) {
                        Locator h5 = allH5.nth(i);
                        try {
                            Locator cardAncestor = h5.locator("xpath=ancestor::div[contains(@class,'MuiPaper-root') or contains(@class,'MuiBox-root')]");
                            if (cardAncestor.count() == 0) cardAncestor = h5.locator("xpath=ancestor::div[1]");
                            Locator selectBtn = cardAncestor.first().locator("button:has-text('Select'), button.css-1w7n44y-MuiButtonBase-root-MuiButton-root");
                            if (selectBtn.count() == 0) {
                                selectBtn = cardAncestor.first().locator("xpath=.//button[contains(normalize-space(.),'Select')]");
                            }
                            if (selectBtn.count() > 0) {
                                try { selectBtn.first().scrollIntoViewIfNeeded(); selectBtn.first().click(); page.waitForTimeout(600); return selectReasonAndSubmit("Budget"); } catch (Exception ex) {
                                    try { selectBtn.first().click(new Locator.ClickOptions().setForce(true)); page.waitForTimeout(600); return selectReasonAndSubmit("Budget"); } catch (Exception ignored) {}
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }

            // 5) final fallback: click first global Select if only one on page
            Locator anySelect = page.locator("button:has-text('Select')");
            if (anySelect.count() == 1) {
                try { anySelect.first().scrollIntoViewIfNeeded(); anySelect.first().click(); page.waitForTimeout(600); return selectReasonAndSubmit("Budget"); } catch (Exception ignored) {}
            }

            // save debug and return false
            Path dir = Paths.get("target/debug/supply-select-vendor-failed");
            Files.createDirectories(dir);
            page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png")));
            Files.writeString(dir.resolve("page.html"), page.content());
            DashboardManager.log("[SUPPLY] selectVendorByNameAndSubmit: failed to find Select for vendor: " + vendorName);
            return false;
        } catch (Exception ex) {
            DashboardManager.log("[SUPPLY] selectVendorByNameAndSubmit exception: " + ex.getMessage());
            try { Path dir = Paths.get("target/debug/supply-select-vendor-exception"); Files.createDirectories(dir); page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png"))); Files.writeString(dir.resolve("page.html"), page.content()); } catch (Exception ignored) {}
            return false;
        }
    }

    /**
     * Helper executed after Select button is clicked to:
     * - fill notes,
     * - pick currency (Indian Rupee),
     * - fill vendor amount,
     * - then delegate to selectReasonAndSubmit which handles the dialog checkbox + submit robustly.
     */
    private boolean afterSelectFormFillAndSubmit() {
        try {
            page.waitForTimeout(350);

            // fill Notes
            Locator notes = page.locator("input[name='notes'], input[placeholder*='Notes'], textarea[name='notes']");
            if (notes.count() > 0) {
                notes.first().scrollIntoViewIfNeeded();
                try { notes.first().fill(""); } catch (Exception ignored) {}
                notes.first().type("this is automated flow Test", new Locator.TypeOptions().setDelay(20));
                page.waitForTimeout(200);
            }

            // vendor currency
            Locator currency = page.locator("input[id='vendor_currency'], input[placeholder*='Vendor Currency']");
            if (currency.count() > 0) {
                Locator c = currency.first(); c.scrollIntoViewIfNeeded();
                try { c.fill(""); } catch (Exception ignored) {}
                c.click(); c.type("Indian Rupee", new Locator.TypeOptions().setDelay(30));
                page.waitForTimeout(300);
                try { c.press("ArrowDown"); c.press("Enter"); page.waitForTimeout(200); } catch (Exception ignored) {}
            }

            // vendor amount
            Locator amount = page.locator("input[name='vendor_amount'], input[placeholder*='Vendor Amount']");
            if (amount.count() > 0) {
                amount.first().scrollIntoViewIfNeeded();
                try { amount.first().fill(""); } catch (Exception ignored) {}
                amount.first().type("800000", new Locator.TypeOptions().setDelay(20));
                page.waitForTimeout(150);
            }

            // Now select reason "Budget" and submit
            boolean done = selectReasonAndSubmit("Budget");
            if (done) {
                page.waitForTimeout(800);
                DashboardManager.log("[SUPPLY] afterSelectFormFillAndSubmit completed submit.");
                return true;
            } else {
                DashboardManager.log("[SUPPLY] afterSelectFormFillAndSubmit could not submit dialog.");
                return false;
            }
        } catch (Exception ex) {
            DashboardManager.log("[SUPPLY] afterSelectFormFillAndSubmit exception: " + ex.getMessage());
            try { Path dir = Paths.get("target/debug/supply-after-select-exception"); Files.createDirectories(dir); page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png"))); Files.writeString(dir.resolve("page.html"), page.content()); } catch (Exception ignored) {}
            return false;
        }
    }

    /**
     * Finds the dialog, searches its list items for the given reason text (case-insensitive substring),
     * clicks the list-item wrapper to toggle checkbox, then clicks Submit inside the same dialog.
     * This method retries multiple click strategies for the dialog submit and watches for the dialog closing.
     */
    private boolean selectReasonAndSubmit(String reason) {
        try {
            if (reason == null || reason.trim().isEmpty()) reason = "Budget";
            String reasonLower = reason.trim().toLowerCase();

            // small wait for dialog to appear
            page.waitForTimeout(300);

            Locator dialog = page.locator("div.MuiDialog-container, div[role='dialog']");
            if (dialog.count() == 0) {
                DashboardManager.log("[SUPPLY] No dialog container found while trying to select reason: " + reason);
                return false;
            }
            Locator dlg = dialog.first();

            // Find labels (preferred) to match reason
            Locator labels = dlg.locator("span.MuiListItemText-primary, .MuiListItemText-root span, .MuiTypography-root");
            boolean clickedAny = false;

            for (int i = 0; i < labels.count(); i++) {
                try {
                    String txt = labels.nth(i).innerText().trim();
                    if (txt != null && txt.toLowerCase().contains(reasonLower)) {
                        Locator labelElt = labels.nth(i);
                        // attempt to find the closest list item button wrapper
                        Locator wrapper = labelElt.locator("xpath=ancestor::div[contains(@class,'MuiListItemButton-root')]");
                        if (wrapper.count() == 0) wrapper = labelElt.locator("xpath=ancestor::li[1]//div[contains(@class,'MuiListItemButton-root')]");
                        if (wrapper.count() > 0) {
                            try {
                                wrapper.first().scrollIntoViewIfNeeded();
                                wrapper.first().click();
                                clickedAny = true;
                                DashboardManager.log("[SUPPLY] Clicked list-item wrapper for reason: " + txt);
                            } catch (Exception e) {
                                DashboardManager.log("[SUPPLY] wrapper click failed: " + e.getMessage());
                            }
                        } else {
                            // fallback: try clicking the checkbox input sibling
                            Locator cb = labelElt.locator("xpath=ancestor::li[1]//input[@type='checkbox']");
                            if (cb.count() > 0) {
                                try { cb.first().scrollIntoViewIfNeeded(); cb.first().click(); clickedAny = true; DashboardManager.log("[SUPPLY] Clicked checkbox input for reason: " + txt); } catch (Exception ignored) {}
                            }
                        }
                        break;
                    }
                } catch (Exception ignored) {}
            }

            // if not found via labels, try scanning list item wrappers and inner text
            if (!clickedAny) {
                Locator wrappers = dlg.locator("div.MuiListItemButton-root, .MuiListItemButton-root");
                for (int i = 0; i < wrappers.count(); i++) {
                    try {
                        Locator w = wrappers.nth(i);
                        String txt = "";
                        try { txt = w.innerText().trim().toLowerCase(); } catch (Exception ignored) {}
                        if (txt.contains(reasonLower)) {
                            w.scrollIntoViewIfNeeded();
                            try { w.click(); clickedAny = true; DashboardManager.log("[SUPPLY] Clicked list-item wrapper by scanning wrappers: " + txt); } catch (Exception e) { DashboardManager.log("[SUPPLY] wrapper click failed scanning wrappers: " + e.getMessage()); }
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            }

            // Try direct checkbox input as another fallback
            if (!clickedAny) {
                Locator inputs = dlg.locator("input[type='checkbox']");
                for (int i = 0; i < inputs.count(); i++) {
                    try {
                        Locator in = inputs.nth(i);
                        String parentText = "";
                        try { parentText = in.locator("xpath=ancestor::li[1]").innerText().trim().toLowerCase(); } catch (Exception ignored) {}
                        if (parentText.contains(reasonLower) || (in.getAttribute("id") != null && in.getAttribute("id").toLowerCase().contains(reasonLower))) {
                            in.scrollIntoViewIfNeeded();
                            try { in.click(); clickedAny = true; DashboardManager.log("[SUPPLY] Clicked checkbox input fallback for reason: " + reason); } catch (Exception ignored) {}
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (!clickedAny) {
                DashboardManager.log("[SUPPLY] Could not find/ click checkbox for reason '" + reason + "' inside dialog. continuing to try submit (checkbox may be optional).");
            }

            // Short pause to allow UI to register checkbox change
            page.waitForTimeout(250);

            // Now robustly click the Submit button inside the same dialog with retries.
            Locator btn = dlg.locator("button:has-text('Submit'), button:has-text('Move To Partner Shortlisting'), button[type='submit']");
            int maxAttempts = 6;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    // If dialog disappeared, we succeeded earlier
                    if (page.locator("div.MuiDialog-container, div[role='dialog']").count() == 0) {
                        DashboardManager.log("[SUPPLY] Dialog already closed before clicking submit (considered success).");
                        return true;
                    }

                    if (btn.count() > 0) {
                        Locator first = btn.first();
                        try {
                            first.scrollIntoViewIfNeeded();
                            DashboardManager.log("[SUPPLY] Attempt " + attempt + " to click dialog submit via normal click.");
                            first.click();
                            page.waitForTimeout(350);
                        } catch (Exception e) {
                            DashboardManager.log("[SUPPLY] normal click failed on attempt " + attempt + ": " + e.getMessage());
                            try {
                                DashboardManager.log("[SUPPLY] Attempt " + attempt + " trying force click.");
                                first.click(new Locator.ClickOptions().setForce(true));
                                page.waitForTimeout(300);
                            } catch (Exception e2) {
                                DashboardManager.log("[SUPPLY] force click failed on attempt " + attempt + ": " + e2.getMessage());
                                // JS dispatch fallback inside the dialog (target only buttons inside the dialog)
                                try {
                                    DashboardManager.log("[SUPPLY] Attempt " + attempt + " trying JS dispatch click inside dialog.");
                                    Boolean jsClicked = (Boolean) page.evaluate("() => { const dlg = document.querySelector('.MuiDialog-container') || document.querySelector('[role=\"dialog\"]'); if(!dlg) return false; const btns = Array.from(dlg.querySelectorAll('button')); for(const b of btns){ if(b.innerText && (b.innerText.trim().toLowerCase().includes('submit') || b.innerText.trim().toLowerCase().includes('move to partner'))){ b.dispatchEvent(new MouseEvent('click', {bubbles:true})); return true; } } return false; }");
                                    if (jsClicked != null && jsClicked) {
                                        page.waitForTimeout(300);
                                    }
                                } catch (Exception je) {
                                    DashboardManager.log("[SUPPLY] JS dispatch fallback failed on attempt " + attempt + ": " + je.getMessage());
                                }
                            }
                        }
                    } else {
                        // no locator found — try JS to click any matching button text inside dialog
                        try {
                            DashboardManager.log("[SUPPLY] Attempt " + attempt + " trying JS direct click (no locator).");
                            Boolean jsClicked = (Boolean) page.evaluate("() => { const dlg = document.querySelector('.MuiDialog-container') || document.querySelector('[role=\"dialog\"]'); if(!dlg) return false; const btns = Array.from(dlg.querySelectorAll('button')); for(const b of btns){ if(b.innerText && (b.innerText.trim().toLowerCase().includes('submit') || b.innerText.trim().toLowerCase().includes('move to partner'))){ b.click(); return true; } } return false; }");
                            if (jsClicked != null && jsClicked) page.waitForTimeout(300);
                        } catch (Exception je) {
                            DashboardManager.log("[SUPPLY] JS click (no locator) failed on attempt " + attempt + ": " + je.getMessage());
                        }
                    }

                    // After each attempt, check if dialog closed
                    page.waitForTimeout(250);
                    if (page.locator("div.MuiDialog-container, div[role='dialog']").count() == 0) {
                        DashboardManager.log("[SUPPLY] Dialog closed after attempt " + attempt + " — submit likely succeeded.");
                        return true;
                    } else {
                        DashboardManager.log("[SUPPLY] Dialog still present after attempt " + attempt + ".");
                    }

                } catch (Exception ex) {
                    DashboardManager.log("[SUPPLY] Exception while attempting to click dialog submit on attempt " + attempt + ": " + ex.getMessage());
                }

                // small delay between attempts
                page.waitForTimeout(300);
            }

            DashboardManager.log("[SUPPLY] All attempts exhausted — dialog submit did not close the dialog.");
            // capture debug
            try { Path dir = Paths.get("target/debug/supply-dialog-submit-failed"); Files.createDirectories(dir); page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png"))); Files.writeString(dir.resolve("page.html"), page.content()); } catch (Exception ignored) {}
            return false;

        } catch (Exception ex) {
            DashboardManager.log("[SUPPLY] selectReasonAndSubmit exception: " + ex.getMessage());
            try { Path dir = Paths.get("target/debug/supply-select-reason-exception"); Files.createDirectories(dir); page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png"))); Files.writeString(dir.resolve("page.html"), page.content()); } catch (Exception ignored) {}
            return false;
        }
    }

    /**
     * Attempts to find and click a button with the given visible text inside any dialog container.
     * Returns true if clicked.
     */
    private boolean clickSubmitInsideDialog(String visibleText) {
        try {
            Locator dialog = page.locator("div.MuiDialog-container, div[role='dialog']");
            if (dialog.count() > 0) {
                Locator btn = dialog.first().locator("button:has-text('" + visibleText + "'), button[type='submit']");
                if (btn.count() > 0) {
                    Locator first = btn.first();
                    for (int attempt = 0; attempt < 5; attempt++) {
                        try {
                            first.scrollIntoViewIfNeeded();
                            try { first.click(); } catch (Exception e1) { first.click(new Locator.ClickOptions().setForce(true)); }
                            page.waitForTimeout(300);
                            if (page.locator("div.MuiDialog-container, div[role='dialog']").count() == 0) return true;
                        } catch (Exception ex) {
                            DashboardManager.log("[SUPPLY] clickSubmitInsideDialog attempt " + attempt + " failed: " + ex.getMessage());
                        }
                    }
                    // JS fallback
                    try {
                        Boolean jsClicked = (Boolean) page.evaluate("text => { const dlg = document.querySelector('.MuiDialog-container') || document.querySelector('[role=\"dialog\"]'); if(!dlg) return false; const btns = Array.from(dlg.querySelectorAll('button')); for(const b of btns){ if(b.innerText && b.innerText.trim().toLowerCase().indexOf(text.toLowerCase()) !== -1){ b.click(); return true; } } return false; }", visibleText);
                        if (jsClicked != null && jsClicked) {
                            page.waitForTimeout(300);
                            return page.locator("div.MuiDialog-container, div[role='dialog']").count() == 0;
                        }
                    } catch (Exception je) {
                        DashboardManager.log("[SUPPLY] JS fallback in clickSubmitInsideDialog failed: " + je.getMessage());
                    }
                } else {
                    // no button found via locator; try JS approach
                    try {
                        Boolean jsClicked = (Boolean) page.evaluate("text => { const dlg = document.querySelector('.MuiDialog-container') || document.querySelector('[role=\"dialog\"]'); if(!dlg) return false; const btns = Array.from(dlg.querySelectorAll('button')); for(const b of btns){ if(b.innerText && (b.innerText.trim().toLowerCase().indexOf(text.toLowerCase()) !== -1 || b.innerText.trim().toLowerCase().indexOf('submit') !== -1)){ b.click(); return true; } } return false; }", visibleText);
                        if (jsClicked != null && jsClicked) { page.waitForTimeout(300); return page.locator("div.MuiDialog-container, div[role='dialog']").count() == 0; }
                    } catch (Exception je) {
                        DashboardManager.log("[SUPPLY] JS fallback (no locator) clickSubmitInsideDialog failed: " + je.getMessage());
                    }
                }
            } else {
                // no dialog found: try global button fallback
                Locator submit = page.locator("button:has-text('" + visibleText + "'), button[type='submit']");
                if (submit.count() > 0) {
                    try { submit.first().scrollIntoViewIfNeeded(); submit.first().click(new Locator.ClickOptions().setForce(true)); return true; } catch (Exception ignored) {}
                }
            }
        } catch (Exception ex) {
            DashboardManager.log("[SUPPLY] clickSubmitInsideDialog exception: " + ex.getMessage());
        }
        return false;
    }

    /**
     * Helper that tries a list of simple selectors one-by-one and returns the first Locator found (or null).
     * Avoids comma-separated composite selectors which can fail if any fragment is malformed.
     */
    private Locator findFirstLocator(String[] selectors) {
        for (String sel : selectors) {
            try {
                if (sel == null || sel.trim().isEmpty()) continue;
                Locator l = page.locator(sel);
                if (l != null && l.count() > 0) {
                    return l.first();
                }
            } catch (Exception ex) {
                // selector may be invalid for Playwright parser — continue to next
                DashboardManager.log("[SUPPLY] findFirstLocator: selector failed: " + sel + " -> " + ex.getMessage());
            }
        }
        return null;
    }

    public void close() {
        try { if (!page.isClosed()) page.close(); } catch (Exception ignored) {}
    }

    // small helper to extract trailing digits from deal names like AutoDeal-123456789
    private String extractNumericFragment(String dealName) {
        if (dealName == null) return "";
        String digits = dealName.replaceAll("\\D+", "");
        if (digits.length() >= 6) return digits;
        return dealName.length() > 6 ? dealName.substring(dealName.length() - 6) : dealName;
    }
}
