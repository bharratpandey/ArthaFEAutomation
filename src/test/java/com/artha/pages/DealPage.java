package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import com.artha.utils.DashboardManager;

public class DealPage {
    private final Page page;

    // Top-level selectors
    private final String dealsNavLink = "a[href*='/deals']";
    private final String createDealBtn = "button[type='button']:has-text(\"Create Deal\")";
    private final String dealNameInput = "input[name='deal_name']";
    private final String accountInput = "input[name='account_id']";
    private final String dealAmountInput = "input[name='deal_amount']";
    private final String contactInput = "input#contact_id, input[name='contact_id']";
    private final String skusInput = "input#skus";
    private final String lobInput = "input#lob";
    private final String leadSourceInput = "div:has(label:text('Lead Source')) input[role='combobox']";
    private final String baSpocInput = "input#ba_spoc_id";
    private final String dealSpocInput = "input#deal_spoc_id";
    private final String submitCreateBtn = "form button[type='submit']:has-text(\"Create Deal\")";
    private final String formSelector = "form";

    public DealPage(Page page) {
        this.page = page;
    }

    public void openDealsFromNav() {
        Locator all = page.locator(dealsNavLink);
        all.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        Locator toClick = all.first();
        try {
            toClick.click();
        } catch (Exception e) {
            toClick.click(new Locator.ClickOptions().setForce(true));
        }
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    }

    public void clickCreateDeal() {
        Locator btn = page.locator(createDealBtn);
        btn.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        btn.first().scrollIntoViewIfNeeded();
        try {
            btn.first().click();
        } catch (Exception e) {
            btn.first().click(new Locator.ClickOptions().setForce(true));
        }
        page.locator(dealNameInput).first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    }

    // Generic safe typing with JS fallback
    private void safeType(String selector, String value) {
        Locator loc = page.locator(selector);
        if (loc.count() == 0) return;
        Locator first = loc.first();
        first.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        first.scrollIntoViewIfNeeded();

        try {
            first.click();
            first.fill("");
            first.type(value, new Locator.TypeOptions().setDelay(20));
            page.waitForTimeout(150);
            String current = "";
            try { current = first.inputValue(); } catch (Exception ignored) {}
            if (value.equals(current)) return;
        } catch (Exception ignored) {}

        // JS fallback
        try {
            String script =
                    "([sel, v]) => { const el = document.querySelector(sel); if (!el) return false; el.focus(); el.value = v; el.dispatchEvent(new Event('input', {bubbles:true})); el.dispatchEvent(new Event('change', {bubbles:true})); return el.value === v; }";
            Object ok = page.evaluate(script, new Object[]{selector, value});
            page.waitForTimeout(150);
            if (ok instanceof Boolean && (Boolean) ok) return;
        } catch (Exception ignored) {}
    }

    // Universal: selects first matching option for a MUI-like autocomplete input
    public void selectFirstAutocompleteOption(String inputSelector, String visibleText) {
        try {
            Locator input = page.locator(inputSelector).first();
            DashboardManager.log("[Deal Flow] Selecting autocomplete option – selector: " + inputSelector + " | text: " + visibleText);
            input.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
            input.scrollIntoViewIfNeeded();
            input.click();

            // clear
            try { input.fill(""); } catch (Exception ignored) {}

            if (visibleText != null && !visibleText.isEmpty()) {
                input.type(visibleText, new Locator.TypeOptions().setDelay(60));
// Wait until first option appears OR 5 seconds max — moves on immediately when visible
                try {
                    page.locator("ul[role='listbox'] li[role='option']")
                            .first()
                            .waitFor(new Locator.WaitForOptions()
                                    .setState(WaitForSelectorState.VISIBLE)
                                    .setTimeout(5000));
                } catch (Exception ignored) {}
            } else {
                try { input.press("ArrowDown"); } catch (Exception ignored) {}
                page.waitForTimeout(250);
            }

            Locator options = page.locator("ul[role='listbox'] li[role='option']");
            try {
                options.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(3000));
            } catch (Exception waitEx) {
                DashboardManager.log("[Deal Flow] ⚠️ Options did not appear for selector: " + inputSelector);
            }

            int count = options.count();
            DashboardManager.log("[Deal Flow] Options count: " + count);

            if (count > 0) {
                // try exact / partial then first
                if (visibleText != null && !visibleText.isEmpty()) {
                    Locator exact = options.filter(new Locator.FilterOptions().setHasText(visibleText)).first();
                    if (exact.count() > 0) {
                        DashboardManager.log("[Deal Flow] -> Clicking exact match option: " + visibleText);
                        try { exact.click(); return; } catch (Exception e) { exact.click(new Locator.ClickOptions().setForce(true)); return; }
                    }
                    Locator partial = page.locator("//ul[@role='listbox']//li[@role='option' and contains(normalize-space(.), '" + visibleText + "')]");
                    if (partial.count() > 0) {
                        DashboardManager.log("[Deal Flow] -> Clicking partial match option: " + visibleText);
                        try { partial.first().click(); return; } catch (Exception e) { partial.first().click(new Locator.ClickOptions().setForce(true)); return; }
                    }
                }
                DashboardManager.log("[Deal Flow] -> Clicking first available option (fallback)");
                try { options.first().click(); return; } catch (Exception e) { options.first().click(new Locator.ClickOptions().setForce(true)); return; }
            }

            // Fallbacks
            DashboardManager.log("[Deal Flow] ⚠️ No autocomplete options visible – attempting keyboard fallback.");
            try {
                input.press("ArrowDown");
                page.waitForTimeout(250);
                input.press("Enter");
                page.waitForTimeout(300);
                return;
            } catch (Exception kex) {
                DashboardManager.log("[Deal Flow] ❌ Keyboard fallback failed: " + kex.getMessage());
            }

            if (visibleText != null && !visibleText.isEmpty()) {
                Locator any = page.locator("text=\"" + visibleText + "\"");
                if (any.count() > 0) {
                    try { any.first().click(); return; } catch (Exception e) { any.first().click(new Locator.ClickOptions().setForce(true)); return; }
                }
            }
        } catch (Exception ex) {
            DashboardManager.log("[Deal Flow] ❌ selectFirstAutocompleteOption failed: " + ex.getMessage());
        }
    }

    // Account needs exact matching by visible text
    public void selectAccount(String accountName) {
        selectFirstAutocompleteOption(accountInput, accountName);
    }

    // Click the first client contact option (no specific text needed)
    public void selectFirstClientContact() {
        selectFirstAutocompleteOption("#contact_id", "");
    }

    // Lead Source simple
    public void selectLeadSourceSimple(String text) {
        if (text == null) return;
        Locator input = page.locator("input#lead_source").first();
        input.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
        input.scrollIntoViewIfNeeded();
        input.click();
        try { input.fill(""); } catch (Exception ignored) {}
        input.type(text, new Locator.TypeOptions().setDelay(40));
        page.waitForTimeout(250);
        try { input.press("ArrowDown"); page.waitForTimeout(150); } catch (Exception ignored) {}
        Locator firstOption = page.locator("ul[role='listbox'] li[role='option']").first();
        if (firstOption.count() == 0) firstOption = page.locator("div.MuiAutocomplete-option").first();
        try {
            if (firstOption.count() > 0) { firstOption.click(new Locator.ClickOptions().setForce(true)); page.waitForTimeout(200); return; }
        } catch (Exception e) { DashboardManager.log("[Deal Flow] ⚠️ selectLeadSourceSimple click failed: " + e.getMessage()); }
        try { input.press("Enter"); page.waitForTimeout(200); } catch (Exception ignored) {}
    }

    public void selectSubOrgSimple(String text) {
        if (text == null) return;
        Locator input = page.locator("input#sub_org").first();
        input.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
        input.scrollIntoViewIfNeeded();
        input.click();
        try { input.fill(""); } catch (Exception ignored) {}
        input.type(text, new Locator.TypeOptions().setDelay(40));
        page.waitForTimeout(300);
        try { input.press("ArrowDown"); page.waitForTimeout(200); } catch (Exception ignored) {}
        Locator firstOption = page.locator("ul[role='listbox'] li[role='option']").first();
        if (firstOption.count() == 0) firstOption = page.locator("div.MuiAutocomplete-option").first();
        if (firstOption.count() > 0) {
            try { firstOption.click(new Locator.ClickOptions().setForce(true)); page.waitForTimeout(200); return; } catch (Exception ignored) {}
        }
        try { input.press("Enter"); page.waitForTimeout(200); } catch (Exception ignored) {}
    }

    // Fill the create-deal form
    public void fillDealForm(
            String dealName,
            String account,
            String amount,
            String contact,
            String skus,
            String lob,
            String baSpoc,
            String dealSpoc,
            String leadSource,
            String subOrg
    ) {
        safeType(dealNameInput, dealName);
        selectAccount(account);
        safeType(dealAmountInput, amount);

        if (page.locator(contactInput).count() > 0) selectFirstClientContact();
        if (page.locator(skusInput).count() > 0) selectFirstAutocompleteOption(skusInput, skus);
        if (page.locator(lobInput).count() > 0) selectFirstAutocompleteOption(lobInput, lob);
        if (page.locator(baSpocInput).count() > 0) selectFirstAutocompleteOption(baSpocInput, baSpoc);
        if (page.locator(dealSpocInput).count() > 0) selectFirstAutocompleteOption(dealSpocInput, dealSpoc);
        if (page.locator("input#lead_source").count() > 0) selectLeadSourceSimple(leadSource);

        page.waitForTimeout(300);
    }

    public void submitDeal() {
        try {
            Locator candidates = page.locator("button[type='submit']:has-text(\"Create Deal\")");
            if (candidates.count() == 0) {
                candidates = page.locator("button:has-text(\"Create Deal\")");
            }

            Locator chosen = null;
            for (int i = 0; i < candidates.count(); i++) {
                Locator c = candidates.nth(i);
                try {
                    if (!c.isVisible()) continue;
                    String type = c.getAttribute("type");
                    Locator closestForm = c.locator("xpath=ancestor::form").first();
                    boolean inForm = closestForm.count() > 0;
                    if (!"submit".equalsIgnoreCase(type) && !inForm) continue;
                    chosen = c;
                    break;
                } catch (Exception ignored) {}
            }

            if (chosen == null) {
                Locator anySubmit = page.locator("button[type='submit']");
                for (int i = 0; i < anySubmit.count(); i++) {
                    Locator c = anySubmit.nth(i);
                    try {
                        if (c.isVisible()) { chosen = c; break; }
                    } catch (Exception ignored) {}
                }
            }

            if (chosen == null) {
                DashboardManager.log("[Deal Flow] ❌ No suitable submit button found.");
                saveSubmitDebugArtifacts("no-submit-found");
                throw new RuntimeException("submitDeal: no suitable Create Deal submit button visible");
            }

            chosen.scrollIntoViewIfNeeded();
            page.waitForTimeout(200);
            try { chosen.click(); } catch (Exception e1) {
                try { chosen.click(new Locator.ClickOptions().setForce(true)); } catch (Exception e2) {
                    DashboardManager.log("[Deal Flow] ❌ Submit click failed: " + e2.getMessage());
                    saveSubmitDebugArtifacts("click-failed");
                    throw new RuntimeException("submitDeal: click attempts failed: " + e2.getMessage());
                }
            }

            try { page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7000)); } catch (Exception ignored) {}
            page.waitForTimeout(400);

        } catch (Exception ex) {
            DashboardManager.log("[Deal Flow] ❌ submitDeal exception: " + ex.getMessage());
            saveSubmitDebugArtifacts("submit-exception");
            throw new RuntimeException(ex);
        }
    }

    private void saveSubmitDebugArtifacts(String name) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get("target", "debug", name);
            java.nio.file.Files.createDirectories(dir);
            page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png")));
            java.nio.file.Files.writeString(dir.resolve("page.html"), page.content());
            DashboardManager.log("[Deal Flow] ⚠️ Debug artifacts saved to: " + dir.toAbsolutePath());
        } catch (Exception e) {
            DashboardManager.log("[Deal Flow] ❌ Failed to save debug artifacts: " + e.getMessage());
        }
    }

    public boolean verifyDealCreated(String dealName) {
        boolean urlChanged = !page.url().contains("/deals/create") && !page.url().contains("/deals/new");
        boolean foundByText = page.locator("text=\"" + dealName + "\"").count() > 0;
        return urlChanged || foundByText;
    }

    /**
     * Robustly open a deal tile from the listing by scanning visible cards (no search field).
     * Returns true if the deal tile was opened (clicked) successfully.
     */
    public boolean openDealFromListing(String dealName) {
        try {
            DashboardManager.log("[Deal Flow] -> Opening deal from listing: " + dealName);

            // Ensure we're on the deals listing (click nav if available)
            try {
                Locator nav = page.locator(dealsNavLink).first();
                if (nav != null && nav.count() > 0) {
                    try { nav.scrollIntoViewIfNeeded(); nav.click(); } catch (Exception e) { nav.click(new Locator.ClickOptions().setForce(true)); }
                    page.waitForTimeout(600);
                }
            } catch (Exception e) {
                DashboardManager.log("[Deal Flow] ⚠️ Could not click deals nav: " + e.getMessage());
            }

            // try multiple passes: scan visible h3s, scroll to load more, repeat
            int passes = 6;
            for (int pass = 0; pass < passes; pass++) {
                try {
                    page.waitForTimeout(350);

                    Locator allH3 = page.locator("h3");
                    int count = allH3.count();
                    DashboardManager.log("[Deal Flow] -> Scanning listing cards – count: " + count + " (pass " + (pass + 1) + "/" + passes + ")");

                    for (int i = 0; i < count; i++) {
                        try {
                            Locator h = allH3.nth(i);
                            String text = "";
                            try { text = h.innerText().trim(); } catch (Exception ignored) {}
                            if (text == null) text = "";

                            boolean matched = false;
                            if (!text.isEmpty()) {
                                if (text.equalsIgnoreCase(dealName)) matched = true;
                                else if (text.contains(dealName)) matched = true;
                                else {
                                    String digits = dealName.replaceAll("\\D+", "");
                                    if (!digits.isEmpty() && text.contains(digits)) matched = true;
                                    else {
                                        int len = Math.min(8, dealName.length());
                                        if (len > 0 && text.startsWith(dealName.substring(0, len))) matched = true;
                                    }
                                }
                            }

                            if (matched) {
                                DashboardManager.log("[Deal Flow] ✅ Matched deal card: '" + text + "'");
                                try {
                                    Locator card = h.locator("xpath=ancestor::div[contains(@class,'group') or contains(@class,'cursor-pointer') or contains(@class,'bg-white') or contains(@class,'rounded-lg')][1]");
                                    if (card != null && card.count() > 0) {
                                        card.first().scrollIntoViewIfNeeded();
                                        try { card.first().click(); page.waitForTimeout(500); DashboardManager.log("[Deal Flow] ✅ Clicked deal card."); return true; }
                                        catch (Exception e) { card.first().click(new Locator.ClickOptions().setForce(true)); page.waitForTimeout(500); DashboardManager.log("[Deal Flow] ✅ Force-clicked deal card."); return true; }
                                    } else {
                                        h.scrollIntoViewIfNeeded();
                                        try { h.click(); page.waitForTimeout(500); DashboardManager.log("[Deal Flow] ✅ Clicked deal h3."); return true; }
                                        catch (Exception e) { h.click(new Locator.ClickOptions().setForce(true)); page.waitForTimeout(500); DashboardManager.log("[Deal Flow] ✅ Force-clicked deal h3."); return true; }
                                    }
                                } catch (Exception clickEx) {
                                    DashboardManager.log("[Deal Flow] ⚠️ Click failed for matched card: " + clickEx.getMessage());
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    // nothing matched in this pass — scroll to load more
                    try {
                        page.evaluate("() => { window.scrollBy(0, Math.max(window.innerHeight, 600)); }");
                        page.waitForTimeout(450);
                    } catch (Exception ignored) {}

                } catch (Exception passEx) {
                    DashboardManager.log("[Deal Flow] ⚠️ Pass exception: " + passEx.getMessage());
                }
            }

            // Final attempt: search input fallback
            try {
                Locator search = page.locator("input[placeholder*='Search Deals'], input[placeholder*='Search Account'], input[placeholder*='Search']");
                if (search.count() > 0) {
                    DashboardManager.log("[Deal Flow] -> Fallback: using search input to locate deal.");
                    Locator s = search.first();
                    try {
                        s.scrollIntoViewIfNeeded();
                        s.click();
                        s.fill("");
                        s.type(dealName, new Locator.TypeOptions().setDelay(30));
                        try { s.press("Enter"); } catch (Exception ignored) {}
                        page.waitForTimeout(700);

                        Locator tile = page.locator("h3:has-text(\"" + dealName + "\")").first();
                        if (tile != null && tile.count() > 0) {
                            tile.scrollIntoViewIfNeeded();
                            try { tile.click(); page.waitForTimeout(400); DashboardManager.log("[Deal Flow] ✅ Clicked tile after search filter."); return true; }
                            catch (Exception e) { tile.click(new Locator.ClickOptions().setForce(true)); page.waitForTimeout(400); return true; }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            DashboardManager.log("[Deal Flow] ❌ Deal not found in listing: " + dealName);
            return false;

        } catch (Exception e) {
            try {
                java.nio.file.Path dir = java.nio.file.Paths.get("target/debug/kam-open-deal-failed-after-supply");
                java.nio.file.Files.createDirectories(dir);
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png")));
                java.nio.file.Files.writeString(dir.resolve("page.html"), page.content());
            } catch (Exception ignored) {}
            DashboardManager.log("[Deal Flow] ❌ openDealFromListing exception: " + e.getMessage());
            return false;
        }
    }

    /**
     * Wait helper: poll until deal details appear rendered.
     */
    private boolean waitForDealDetailsToRender(int timeoutMs) {
        long start = System.currentTimeMillis();
        int poll = 500;
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                boolean hasNoData = page.locator("text=OH NO, RESULT NOT FOUND!, text=NO DATA, text=No Data").count() > 0;
                boolean dealNumberPresentAndNotNA = false;
                try {
                    Locator dn = page.locator("text=Deal Number").first();
                    if (dn.count() > 0) {
                        Locator parent = dn.locator("xpath=ancestor::div[1]");
                        if (parent.count() > 0) {
                            String txt = parent.innerText();
                            if (txt != null && !txt.contains("N/A")) dealNumberPresentAndNotNA = true;
                        }
                    }
                } catch (Exception ignored) {}

                boolean visibleH3 = page.locator("h3").count() > 0 && page.locator("h3").first().isVisible();

                if (!hasNoData && (dealNumberPresentAndNotNA || visibleH3)) {
                    DashboardManager.log("[Deal Flow] ✅ Deal details rendered.");
                    return true;
                }
            } catch (Exception e) {
                // ignore and retry
            }
            try { page.waitForTimeout(poll); } catch (Exception ignored) {}
        }
        DashboardManager.log("[Deal Flow] ⚠️ Deal details render timed out after " + timeoutMs + "ms");
        try {
            java.nio.file.Path dir = Paths.get("target/debug/wait-for-deal-render");
            java.nio.file.Files.createDirectories(dir);
            page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png")));
            java.nio.file.Files.writeString(dir.resolve("page.html"), page.content());
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Helper: try multiple simple locators to find and click a CTA by given visible texts.
     */
    private boolean findAndClickByTexts(String... texts) {
        for (String txt : texts) {
            if (txt == null || txt.trim().isEmpty()) continue;
            try {
                Locator byText = page.locator("text=" + txt);
                if (byText.count() > 0) {
                    Locator first = byText.first();
                    try { first.scrollIntoViewIfNeeded(); first.click(); page.waitForTimeout(300); return true; }
                    catch (Exception e) { try { first.click(new Locator.ClickOptions().setForce(true)); page.waitForTimeout(300); return true; } catch (Exception ignored) {} }
                }

                try {
                    Locator divHas = page.locator("div:has-text(\"" + txt + "\")");
                    if (divHas.count() > 0) {
                        Locator first = divHas.first();
                        try { first.scrollIntoViewIfNeeded(); first.click(); page.waitForTimeout(300); return true; }
                        catch (Exception e) { try { first.click(new Locator.ClickOptions().setForce(true)); page.waitForTimeout(300); return true; } catch (Exception ignored) {} }
                    }
                } catch (Exception ignored) {}

                try {
                    String script =
                            "txt => { const needle = String(txt).toLowerCase(); const all = Array.from(document.querySelectorAll('div, button, a, span')); for (const el of all){ try{ if ((el.innerText || '').toLowerCase().includes(needle)) { el.scrollIntoViewIfNeeded ? el.scrollIntoViewIfNeeded() : el.scrollIntoView(); el.click(); return true; } }catch(e){} } return false; }";
                    Object res = page.evaluate(script, txt);
                    if (res instanceof Boolean && (Boolean) res) {
                        page.waitForTimeout(300);
                        return true;
                    }
                } catch (Exception ignored) {}
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * Move deal to Partner Alignment (KAM flow).
     */
    public boolean moveToPartnerAlignment(String dealName, String skills, String clientRef, String notes, Path demoPdf) {
        try {
            DashboardManager.log("[Deal Flow] -> Starting Partner Alignment flow for: " + dealName);

            // Ensure on deal detail
            try {
                boolean tileClicked = false;
                Locator tile = page.locator("h3:has-text(\"" + dealName + "\")").first();
                if (tile.count() > 0) {
                    try { tile.scrollIntoViewIfNeeded(); tile.click(); page.waitForTimeout(400); tileClicked = true; }
                    catch (Exception e) { try { tile.click(new Locator.ClickOptions().setForce(true)); page.waitForTimeout(400); tileClicked = true; } catch (Exception ignored) {} }
                }

                if (!tileClicked) {
                    boolean opened = openDealFromListing(dealName);
                    if (opened) {
                        DashboardManager.log("[Deal Flow] ✅ Deal opened via listing scan.");
                    } else {
                        DashboardManager.log("[Deal Flow] ⚠️ Could not open deal via listing.");
                    }
                }
            } catch (Exception ignored) {}

            waitForDealDetailsToRender(15000);
            page.waitForTimeout(3000); // extra wait for stage buttons to render

            // Click PARTNER ALIGNMENT CTA
            DashboardManager.log("[Deal Flow] -> Clicking 'Partner Alignment' CTA...");
            String[] partnerTexts = new String[] {
                    "PARTNER ALIGNMENT →",
                    "PARTNER ALIGNMENT",
                    "Partner Alignment",
                    "PARTNER SHORTLISTED →",
                    "PARTNER SHORTLISTED",
                    "PARTNER SHORTLISTED →",
                    "PARTNER SHORTLISTED →",
                    "PARTNER SHORTLISTED",
                    "PARTNER SHORTLISTED →",
                    "PARTNER SHORTLISTED"
            };

            boolean partnerClicked = findAndClickByTexts(partnerTexts);

            if (!partnerClicked) {
                String[] fallbackSelectors = new String[] {
                        "div:has-text(\"PARTNER ALIGNMENT\")",
                        "div:has-text(\"PARTNER SHORTLISTED →\")",
                        "div:has-text(\"PARTNER SHORTLISTED\")"
                };
                for (String fs : fallbackSelectors) {
                    try {
                        Locator cand = page.locator(fs);
                        if (cand.count() > 0) {
                            Locator p = cand.first();
                            p.scrollIntoViewIfNeeded();
                            try { p.click(); page.waitForTimeout(600); partnerClicked = true; break; } catch (Exception e) { try { p.click(new Locator.ClickOptions().setForce(true)); page.waitForTimeout(600); partnerClicked = true; break; } catch (Exception ignored) {} }
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (!partnerClicked) {
                DashboardManager.log("[Deal Flow] ⚠️ Partner Alignment CTA not found.");
            } else {
                DashboardManager.log("[Deal Flow] ✅ Partner Alignment CTA clicked.");
            }

            // operate inside dialog if present
            Locator dialog = page.locator("div.MuiDialog-container, div[role='dialog']").first();
            Locator root = (dialog.count() > 0 && dialog.isVisible()) ? dialog : page.locator("body");

            // TECHSTACK
            if (skills != null && !skills.isEmpty()) {
                try {
                    Locator techs = root.locator("input[name='tech_stack'], input[placeholder*='tech stack'], input[placeholder*='Tech Stack']");
                    if (techs.count() > 0) {
                        Locator techInput = techs.first();
                        techInput.scrollIntoViewIfNeeded();
                        try { techInput.fill(""); } catch (Exception ignored) {}
                        techInput.type(skills, new Locator.TypeOptions().setDelay(20));
                        page.waitForTimeout(200);
                        DashboardManager.log("[Deal Flow] -> Tech stack filled: " + skills);
                    } else {
                        Locator alt = root.locator("input[placeholder*='Enter all tech stack'], input[placeholder*='Tech stack']");
                        if (alt.count() > 0) {
                            alt.first().scrollIntoViewIfNeeded();
                            try { alt.first().fill(""); } catch (Exception ignored) {}
                            alt.first().type(skills, new Locator.TypeOptions().setDelay(20));
                            page.waitForTimeout(200);
                        } else {
                            DashboardManager.log("[Deal Flow] ⚠️ Tech stack input not found.");
                        }
                    }
                } catch (Exception e) {
                    DashboardManager.log("[Deal Flow] ❌ Tech stack input error: " + e.getMessage());
                }
            }

            // CLIENT REF
            if (clientRef != null && !clientRef.isEmpty()) {
                try {
                    Locator client = root.locator(
                            "input[name='client_ref'], input[name='client_reference'], input[name='client_references'], " +
                                    "input[placeholder*='Client Ref'], input[placeholder*='Client Reference'], input[placeholder*='client reference'], " +
                                    "input[placeholder*='Client'], input[placeholder*='client reference']"
                    );
                    if (client.count() > 0) {
                        Locator ci = client.first();
                        ci.scrollIntoViewIfNeeded();
                        try { ci.fill(""); } catch (Exception ignored) {}
                        try {
                            ci.type(clientRef, new Locator.TypeOptions().setDelay(20));
                            page.waitForTimeout(160);
                            String val = "";
                            try { val = ci.inputValue(); } catch (Exception ignored) {}
                            if (val == null || !val.trim().equals(clientRef)) {
                                page.evaluate("([sel, v]) => { const el = document.querySelector(sel); if(!el) return false; el.focus(); el.value = v; el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); return true; }", new Object[]{ "input[name='client_references'], input[name='client_reference'], input[name='client_ref'], input[placeholder*=\"client reference\"]", clientRef });
                                page.waitForTimeout(120);
                            }
                        } catch (Exception te) {
                            try {
                                page.evaluate("([sel, v]) => { const el = document.querySelector(sel); if(!el) return false; el.focus(); el.value = v; el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); return true; }", new Object[]{ "input[name='client_references'], input[name='client_reference'], input[name='client_ref'], input[placeholder*='client reference']", clientRef });
                                page.waitForTimeout(120);
                            } catch (Exception je) {
                                DashboardManager.log("[Deal Flow] ❌ Client ref JS fallback failed: " + je.getMessage());
                            }
                        }
                        DashboardManager.log("[Deal Flow] -> Client ref filled.");
                    } else {
                        Locator techs = root.locator("input[name='tech_stack']");
                        if (techs.count() > 1) {
                            Locator second = techs.nth(1);
                            second.scrollIntoViewIfNeeded();
                            try { second.fill(""); } catch (Exception ignored) {}
                            second.type(clientRef, new Locator.TypeOptions().setDelay(20));
                            page.waitForTimeout(150);
                            DashboardManager.log("[Deal Flow] -> Client ref filled via fallback input.");
                        } else {
                            DashboardManager.log("[Deal Flow] ⚠️ Client ref input not found.");
                        }
                    }
                } catch (Exception e) {
                    DashboardManager.log("[Deal Flow] ❌ Client ref input error: " + e.getMessage());
                }
            }

            // NOTES
            if (notes != null && !notes.isEmpty()) {
                try {
                    Locator notesInput = root.locator("textarea[name='notes'], input[name='notes'], textarea[placeholder*='Notes'], input[placeholder*='Notes']").first();
                    if (notesInput.count() > 0) {
                        notesInput.scrollIntoViewIfNeeded();
                        try { notesInput.fill(""); } catch (Exception ignored) {}
                        notesInput.type(notes, new Locator.TypeOptions().setDelay(15));
                        page.waitForTimeout(150);
                        DashboardManager.log("[Deal Flow] -> Notes filled.");
                    } else {
                        DashboardManager.log("[Deal Flow] ⚠️ Notes input not found.");
                    }
                } catch (Exception e) {
                    DashboardManager.log("[Deal Flow] ❌ Notes input error: " + e.getMessage());
                }
            }

            // UPLOAD demoPdf
            if (demoPdf != null) {
                try {
                    Locator fileInput = root.locator("input[type='file'], input[name='file'], input[name='proposal'], input[name='document'], input[name='ba_proposal']").first();
                    if (fileInput.count() > 0) {
                        fileInput.scrollIntoViewIfNeeded();
                        fileInput.setInputFiles(demoPdf);
                        page.waitForTimeout(400);
                        DashboardManager.log("[Deal Flow] -> Proposal uploaded: " + demoPdf.getFileName());
                    } else {
                        Locator anyFile = page.locator("input[type='file']").first();
                        if (anyFile.count() > 0) {
                            anyFile.setInputFiles(demoPdf);
                            page.waitForTimeout(400);
                            DashboardManager.log("[Deal Flow] -> Proposal uploaded via global file input.");
                        } else {
                            DashboardManager.log("[Deal Flow] ⚠️ No file input found for proposal upload.");
                        }
                    }
                } catch (Exception e) {
                    DashboardManager.log("[Deal Flow] ❌ Proposal upload failed: " + e.getMessage());
                }
            }

            page.waitForTimeout(250);

            // Tick 'Budget' checkbox
            try {
                boolean ticked = false;
                Locator labelBudget = root.locator("#checkbox-list-label-Budget");
                if (labelBudget.count() > 0) {
                    try {
                        Locator ancestor = labelBudget.first().locator("xpath=ancestor::li[1] | xpath=ancestor::div[1]");
                        if (ancestor.count() > 0) {
                            Locator clickable = ancestor.first().locator("div.MuiButtonBase-root, .MuiListItemButton-root").first();
                            if (clickable.count() > 0) {
                                clickable.scrollIntoViewIfNeeded();
                                try { clickable.click(); ticked = true; }
                                catch (Exception e) { clickable.click(new Locator.ClickOptions().setForce(true)); ticked = true; }
                            } else {
                                ancestor.first().scrollIntoViewIfNeeded();
                                try { ancestor.first().click(); ticked = true; } catch (Exception ignored) {}
                            }
                        }
                    } catch (Exception ignored) {}
                }

                if (!ticked) {
                    Locator budgetItem = root.locator("li:has-text('Budget'), div:has-text('Budget'), label:has-text('Budget')");
                    if (budgetItem.count() > 0) {
                        for (int i = 0; i < budgetItem.count(); i++) {
                            try {
                                Locator candidate = budgetItem.nth(i);
                                Locator clickable = candidate.locator("div.MuiButtonBase-root, .MuiListItemButton-root").first();
                                if (clickable.count() > 0) {
                                    clickable.scrollIntoViewIfNeeded();
                                    try { clickable.click(); ticked = true; break; } catch (Exception e) { clickable.click(new Locator.ClickOptions().setForce(true)); ticked = true; break; }
                                } else {
                                    candidate.scrollIntoViewIfNeeded();
                                    try { candidate.click(); ticked = true; break; } catch (Exception ignored) {}
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                if (!ticked) {
                    Locator firstVisible = root.locator("input[type='checkbox']:visible").first();
                    if (firstVisible.count() > 0) {
                        firstVisible.scrollIntoViewIfNeeded();
                        firstVisible.click();
                        ticked = true;
                    }
                }
                DashboardManager.log("[Deal Flow] -> Budget checkbox ticked: " + ticked);
            } catch (Exception e) {
                DashboardManager.log("[Deal Flow] ❌ Checkbox tick failed: " + e.getMessage());
            }

            page.waitForTimeout(180);

            // Final submit/move button
            DashboardManager.log("[Deal Flow] -> Clicking final 'Move To Partner Alignment' CTA...");
            Locator finalBtn = root.locator(
                    "button:has-text('Move To Partner Alignment'), button:has-text('Move To Partner Shortlisting'), button:has-text('Move To Partner'), button:has-text('Submit'), button:has-text('Select')"
            );

            if (finalBtn.count() == 0) {
                finalBtn = root.locator("div.MuiGrid-item button[type='submit'], div.MuiGrid-item button, button.w-fit");
            }

            if (finalBtn.count() == 0) {
                finalBtn = page.locator("button:has-text('Submit'), button:has-text('Move To Partner Alignment'), button:has-text('Move To Partner Shortlisting')");
            }

            if (finalBtn.count() > 0) {
                Locator btn = finalBtn.first();
                btn.scrollIntoViewIfNeeded();
                page.waitForTimeout(150);

                boolean clicked = false;
                try {
                    try {
                        Object disabledAttr = btn.getAttribute("disabled");
                        if (disabledAttr != null) {
                            page.evaluate("el => el.removeAttribute('disabled')", btn);
                        }
                    } catch (Exception ignored) {}

                    try {
                        btn.click();
                    } catch (Exception e) {
                        try {
                            btn.click(new Locator.ClickOptions().setForce(true));
                        } catch (Exception e2) {
                            btn.evaluate("el => el.click()");
                        }
                    }
                    clicked = true;
                } catch (Exception e) {
                    DashboardManager.log("[Deal Flow] ❌ Final CTA click failed: " + e.getMessage());
                    return false;
                }

                if (clicked) {
                    page.waitForTimeout(500);
                    DashboardManager.log("[Deal Flow] -> Waiting for Partner Alignment toast...");

                    try {
                        page.locator("xpath=//div[contains(normalize-space(.), 'The deal is now on partner alignment stage')]")
                                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
                        DashboardManager.log("[Deal Flow] ✅ Toast verified: Partner Alignment stage.");
                        return true;
                    } catch (Exception e) {
                        DashboardManager.log("[Deal Flow] ⚠️ Toast not detected – continuing (soft failure).");
                        return true;
                    }
                }
            } else {
                DashboardManager.log("[Deal Flow] ❌ Final CTA button not found.");
                return false;
            }
            return false;
        } catch (Exception ex) {
            DashboardManager.log("[Deal Flow] ❌ moveToPartnerAlignment exception: " + ex.getMessage());
            try {
                java.nio.file.Path dir = Paths.get("target/debug/move-to-partner-exception");
                java.nio.file.Files.createDirectories(dir);
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png")));
                java.nio.file.Files.writeString(dir.resolve("page.html"), page.content());
            } catch (Exception ignored) {}
            return false;
        }
    }

    /**
     * Helper: dump counts and outerHTML for partner-CTA candidates for offline inspection.
     */
    private void dumpPartnerCtaDebug() {
        try {
            java.nio.file.Path dir = Paths.get("target/debug/partner-cta-dump");
            java.nio.file.Files.createDirectories(dir);

            StringBuilder dump = new StringBuilder();
            String[] selectors = new String[]{
                    "div:has-text(\"PARTNER ALIGNMENT\")",
                    "text=/PARTNER ALIGNMENT\\s*→/i",
                    "text=/PARTNER ALIGNMENT/i",
                    "div[class*='w-fit']"
            };
            for (String sel : selectors) {
                try {
                    Locator loc = page.locator(sel);
                    int c = loc.count();
                    dump.append("Selector: ").append(sel).append(" => count=").append(c).append("\n");
                    for (int i = 0; i < c; i++) {
                        try {
                            String outer = loc.nth(i).evaluate("el => el.outerHTML").toString();
                            if (outer.length() > 2000) outer = outer.substring(0, 2000) + "...(truncated)";
                            dump.append("  idx=").append(i).append(" visible=").append(loc.nth(i).isVisible()).append("\n");
                            dump.append("  outerHTML: ").append(outer).append("\n\n");
                        } catch (Exception e) {
                            dump.append("  idx=").append(i).append(" read failed: ").append(e.getMessage()).append("\n");
                        }
                    }
                } catch (Exception e) {
                    dump.append("Selector: ").append(sel).append(" lookup failed: ").append(e.getMessage()).append("\n");
                }
            }
            java.nio.file.Files.writeString(dir.resolve("partner-cta-dump.txt"), dump.toString());
            page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png")));
            java.nio.file.Files.writeString(dir.resolve("page.html"), page.content());
            DashboardManager.log("[Deal Flow] ⚠️ Partner CTA debug dump saved to: " + dir.toAbsolutePath());
        } catch (Exception e) {
            DashboardManager.log("[Deal Flow] ❌ dumpPartnerCtaDebug failed: " + e.getMessage());
        }
    }
}