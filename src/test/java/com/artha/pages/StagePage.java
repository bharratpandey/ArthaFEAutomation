package com.artha.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
// We need this for the new wait strategy.
// If you prefer not to add imports, the code below uses the fully qualified name: com.microsoft.playwright.options.WaitForSelectorState
import com.microsoft.playwright.options.WaitForSelectorState;
import com.artha.pages.KycDocumentationPage;
import com.artha.utils.DashboardManager;


public class StagePage {

    private final Page page;

    public StagePage(Page page) {
        this.page = page;
    }

    /**
     * Wait until deal details appear to be rendered on the page.
     * Heuristic:
     * - If "NO DATA" / "OH NO" placeholders present -> not ready
     * - If any visible H3 exists -> ready
     * - If Deal Number label exists and value isn't N/A -> ready
     *
     * Returns true if rendered within timeoutMs, false otherwise.
     */
    private boolean waitForDealDetailsToRender(int timeoutMs) {
        long start = System.currentTimeMillis();
        int poll = 400;
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                boolean hasNoData = page.locator("text=OH NO, RESULT NOT FOUND!, text=NO DATA, text=No Data, text=Result Not Found").count() > 0;
                boolean visibleH3 = page.locator("h3").count() > 0 && page.locator("h3").first().isVisible();

                boolean dealNumberOk = false;
                try {
                    Locator dn = page.locator("text=Deal Number").first();
                    if (dn != null && dn.count() > 0) {
                        Locator parent = dn.locator("xpath=ancestor::div[1]").first();
                        if (parent != null && parent.count() > 0) {
                            String txt = parent.innerText();
                            if (txt != null && !txt.contains("N/A")) dealNumberOk = true;
                        }
                    }
                } catch (Exception ignored) {}

                if (!hasNoData && (visibleH3 || dealNumberOk)) {
                    DashboardManager.log("[STAGE] Detected deal details rendered (hasNoData=" + hasNoData + ", dealNumberOK=" + dealNumberOk + ", visibleH3=" + visibleH3 + ")");
                    return true;
                }
            } catch (Exception ignored) {}
            try { page.waitForTimeout(poll); } catch (Exception ignored) {}
        }

        try {
            java.nio.file.Path dir = java.nio.file.Paths.get("target/debug/wait-for-stage-deal-render");
            java.nio.file.Files.createDirectories(dir);
            page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png")));
            java.nio.file.Files.writeString(dir.resolve("page.html"), page.content());
        } catch (Exception ignored) {}
        DashboardManager.log("[STAGE] waitForDealDetailsToRender timed out after " + timeoutMs + "ms");
        return false;
    }

    /**
     * Small helper: try normal click -> forced click -> JS dispatch on the element as last resort.
     * Returns true if clicked.
     */
    private boolean tryClick(Locator loc, String logicalName) {
        try {
            if (loc == null) return false;
            if (loc.count() == 0) return false;
            loc.scrollIntoViewIfNeeded();

            try {
                loc.click();
                DashboardManager.log("[STAGE] tryClick: clicked normally - " + logicalName);
                return true;
            } catch (Exception e1) {
                try {
                    loc.click(new Locator.ClickOptions().setForce(true));
                    DashboardManager.log("[STAGE] tryClick: forced-click succeeded - " + logicalName);
                    return true;
                } catch (Exception e2) {
                    try {
                        // Use element-level evaluate to remove blocking styles & dispatch MouseEvent
                        Object res = loc.evaluate(
                                "(el) => {" +
                                        " try { el.style.pointerEvents = 'auto'; el.style.visibility='visible'; el.style.opacity='1'; } catch(e) {}" +
                                        " try { el.scrollIntoView({block:'center'}); } catch(e) {}" +
                                        " const ev = new MouseEvent('click', {bubbles:true, cancelable:true, view:window});" +
                                        " el.dispatchEvent(ev);" +
                                        " return true;" +
                                        "}"
                        );
                        DashboardManager.log("[STAGE] tryClick: JS-dispatch attempt done - " + logicalName);
                        return true;
                    } catch (Exception je) {
                        DashboardManager.log("[STAGE] tryClick: JS-dispatch failed - " + logicalName + " -> " + je.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            DashboardManager.log("[STAGE] tryClick top-level error for " + logicalName + ": " + ex.getMessage());
        }
        return false;
    }

    /**
     * Robust click helper:
     * 1) try Playwright locator click
     * 2) if not found or click errors, try XPath normalize-space uppercase matching
     * 3) last resort: JS fallback (internal needle)
     */
    private boolean clickElementByTexts(String[] candidateSelectors, String textContains, int timeoutMs) {
        for (String sel : candidateSelectors) {
            try {
                Locator loc = page.locator(sel).first();
                if (loc != null && loc.count() > 0) {
                    if (tryClick(loc, sel)) {
                        page.waitForTimeout(300);
                        DashboardManager.log("[STAGE] clicked via locator: " + sel);
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        }

        // XPath normalized-case fallback
        try {
            String needle = (textContains == null) ? "" : textContains.toUpperCase().trim();
            String xpath = "xpath=//*[" +
                    "contains(translate(normalize-space(string(.)), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), '" + needle + "')" +
                    "]";
            Locator cand = page.locator(xpath);
            if (cand != null && cand.count() > 0) {
                for (int i = 0; i < cand.count(); i++) {
                    try {
                        Locator el = cand.nth(i);
                        if (tryClick(el, "xpath-fallback-" + i)) {
                            page.waitForTimeout(300);
                            DashboardManager.log("[STAGE] clicked via XPath fallback index=" + i);
                            return true;
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            DashboardManager.log("[STAGE] XPath fallback click error: " + e.getMessage());
        }

        // JS fallback with internal needle
        try {
            String js =
                    "() => {" +
                            "  const needle = 'partner shortlisted';" +
                            "  const nodes = Array.from(document.querySelectorAll('div,button,span,li'));" +
                            "  for (const n of nodes) {" +
                            "    try {" +
                            "      const t = (n.textContent || '').toLowerCase().trim();" +
                            "      if (t.includes(needle)) { n.scrollIntoView({block:'center'}); n.click(); return true; }" +
                            "    } catch(e){}" +
                            "  }" +
                            "  return false;" +
                            "}";
            Object res = page.evaluate(js);
            if (res instanceof Boolean && (Boolean) res) {
                DashboardManager.log("[STAGE] clicked via JS fallback for text: " + textContains);
                page.waitForTimeout(300);
                return true;
            }
        } catch (Exception e) {
            DashboardManager.log("[STAGE] JS fallback click failed for text '" + textContains + "': " + e.getMessage());
        }

        return false;
    }

    /**
     * Robustly pick the first option from an MUI-like autocomplete attached to a given input locator:
     * - click popup indicator if present
     * - wait for ul[role='listbox'] li[role='option'] and click first
     * - fallback to ArrowDown+Enter on input
     */
    private boolean pickFirstAutocompleteOption(Locator rootLocator) {
        try {
            try {
                Locator popupBtn = rootLocator.locator("xpath=following-sibling::div//button | .MuiAutocomplete-endAdornment button");
                if (popupBtn != null && popupBtn.count() > 0) {
                    popupBtn.first().scrollIntoViewIfNeeded();
                    try { popupBtn.first().click(); } catch (Exception e) { popupBtn.first().click(new Locator.ClickOptions().setForce(true)); }
                    page.waitForTimeout(300);
                } else {
                    rootLocator.scrollIntoViewIfNeeded();
                    rootLocator.click();
                    page.waitForTimeout(200);
                }
            } catch (Exception ignored) {}

            Locator options = page.locator("ul[role='listbox'] li[role='option']");
            try {
                options.first().waitFor(new Locator.WaitForOptions().setTimeout(3000));
                options.first().scrollIntoViewIfNeeded();
                try { options.first().click(); } catch (Exception e) { options.first().click(new Locator.ClickOptions().setForce(true)); }
                page.waitForTimeout(250);
                DashboardManager.log("[STAGE] picked first option from ul[role=listbox]");
                return true;
            } catch (Exception waitEx) {
                try {
                    rootLocator.press("ArrowDown");
                    page.waitForTimeout(150);
                    rootLocator.press("Enter");
                    page.waitForTimeout(250);
                    DashboardManager.log("[STAGE] picked option via ArrowDown+Enter fallback");
                    return true;
                } catch (Exception kex) {
                    DashboardManager.log("[STAGE] autocomplete option pick failed: " + kex.getMessage());
                }
            }
        } catch (Exception e) {
            DashboardManager.log("[STAGE] pickFirstAutocompleteOption top-level error: " + e.getMessage());
        }
        return false;
    }

    // ============================
    // PARTNER SHORTLISTED STAGE
    // ============================
    // paste/replace only the partnerShortlisting method in StagePage.java with this updated version
    // ============================
    // replace only the partnerShortlisting method with this
    public boolean partnerShortlisting(
            String vendorName,
            String notes,
            String currency,
            String amount
    ) {
        try {
            DashboardManager.log("[STAGE] Starting Partner Shortlisted stage...");

            // Ensure deal details rendered first
            boolean rendered = waitForDealDetailsToRender(6000);
            if (!rendered) {
                DashboardManager.log("[STAGE] Warning: deal details did not fully render within timeout, continuing anyway.");
            }

            // Try to ensure the page/tab has focus (helps after returning from another window)
            try {
                page.bringToFront();
                page.evaluate("() => { try { window.focus(); } catch(e) {} }");
            } catch (Exception ignored) {}

            // --- Wait & locate CTA candidate robustly (COLOR + IGNORE ARROW strategy) ---
            Locator ctaCandidate = null;
            boolean foundVisibleCta = false;

            // Build a combined locator that prefers the unique Shortlisted color class, then text-only matches.
            Locator combined = page.locator(
                    "div[class*='text-[#4c2ca8]'], " +                       // unique purple for SHORTLISTED
                            "div.cursor-pointer:has-text('PARTNER SHORTLISTED'), " + // ignore trailing arrow
                            "text=/PARTNER\\s*SHORTLISTED/i"                         // regex ignoring case/arrow
            );

            try {
                int c = combined.count();
                for (int i = 0; i < c; i++) {
                    try {
                        Locator candidate = combined.nth(i);
                        if (candidate.isVisible()) {
                            ctaCandidate = candidate;
                            foundVisibleCta = true;
                            break;
                        }
                    } catch (Exception ignored) {}
                }
                // if none visible, fall back to the first match (best-effort)
                if (ctaCandidate == null && c > 0) {
                    ctaCandidate = combined.first();
                }

                // Wait a bit for it to become actionable if we have a candidate
                if (ctaCandidate != null) {
                    try {
                        ctaCandidate.waitFor(new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.VISIBLE)
                                .setTimeout(4000));
                        foundVisibleCta = foundVisibleCta || true;
                        DashboardManager.log("[STAGE] CTA candidate located via color/text locator.");
                    } catch (Exception e) {
                        DashboardManager.log("[STAGE] CTA candidate present but not visible within wait; will continue to click fallbacks.");
                    }
                } else {
                    DashboardManager.log("[STAGE] No CTA candidate found by color/text locator; will continue to click fallbacks.");
                }
            } catch (Exception e) {
                DashboardManager.log("[STAGE] combined locator evaluation failed: " + e.getMessage());
            }

            // candidateSelectors: color-first, then text (ignoring arrow), then alignment fallback
            String[] candidateSelectors = new String[] {
                    "div[class*='text-[#4c2ca8]']",                                   // prefer unique shortlisted color
                    "div.cursor-pointer:has-text('PARTNER SHORTLISTED')",             // ignore trailing arrow/icon
                    "text=/PARTNER\\s*SHORTLISTED/i",                                 // regex text match
                    // alignment fallback (in case markup swapped)
                    "div:has-text('PARTNER ALIGNMENT →')",
                    "div:has-text('PARTNER ALIGNMENT')",
                    // robust xpath normalizing NBSPs/spaces
                    "xpath=//div[contains(normalize-space(translate(string(.), '\u00A0',' ')), 'PARTNER SHORTLISTED')]",
                    "xpath=//div[contains(normalize-space(translate(string(.), '\u00A0',' ')), 'PARTNER ALIGNMENT')]"
            };

            // --- Robust click strategies ---
            boolean clicked = false;

            // 1) Try selectors (color/text first)
            for (String sel : candidateSelectors) {
                try {
                    Locator loc = page.locator(sel).first();
                    if (loc != null && loc.count() > 0) {
                        if (tryClick(loc, "candidate:" + sel)) {
                            page.waitForTimeout(300);
                            clicked = true;
                            DashboardManager.log("[STAGE] clicked via locator: " + sel);
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 2) If a previously found visible candidate exists, try clicking it
            if (!clicked && ctaCandidate != null) {
                try {
                    if (tryClick(ctaCandidate, "direct candidate")) {
                        page.waitForTimeout(250);
                        clicked = true;
                        DashboardManager.log("[STAGE] clicked CTA via direct candidate click.");
                    }
                } catch (Exception ignored) {}
            }

            // 3) Try explicit flex container path (from your DOM hint) as additional fallback
            if (!clicked) {
                try {
                    Locator xpathFlex = page.locator("xpath=//div[@class='flex justify-start items-start gap-5']//div[contains(translate(normalize-space(string(.)), 'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'PARTNER ALIGNMENT')]");
                    if (xpathFlex.count() > 0) {
                        for (int i = 0; i < xpathFlex.count(); i++) {
                            try {
                                Locator el = xpathFlex.nth(i);
                                if (tryClick(el, "xpathFlex-" + i)) {
                                    page.waitForTimeout(300);
                                    clicked = true;
                                    DashboardManager.log("[STAGE] clicked via xpathFlex index=" + i);
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 4) Strong JS fallback (normalize NBSP)
            if (!clicked) {
                try {
                    Object res = page.evaluate(
                            "() => {" +
                                    "  const needle = 'partner shortlisted';" +
                                    "  const needleAlt = 'partner alignment';" +
                                    "  const normalize = s => String(s||'').replace(/\\u00A0/g,' ').toLowerCase().trim();" +
                                    "  const nodes = Array.from(document.querySelectorAll('div, button, a, span'));" +
                                    "  for (const n of nodes) {" +
                                    "    try {" +
                                    "      const t = normalize(n.textContent || n.innerText || '');" +
                                    "      if (!t) continue;" +
                                    "      if (t.includes(needle) || t.includes(needleAlt)) {" +
                                    "        n.scrollIntoView({block:'center'});" +
                                    "        const ev = new MouseEvent('click', {bubbles:true, cancelable:true, view:window});" +
                                    "        n.dispatchEvent(ev);" +
                                    "        return {ok:true, outer: (n.outerHTML||'').slice(0,2000)};" +
                                    "      }" +
                                    "    } catch(e){}" +
                                    "  }" +
                                    "  return {ok:false};" +
                                    "}"
                    );
                    if (res instanceof java.util.Map) {
                        java.util.Map map = (java.util.Map) res;
                        Object ok = map.get("ok");
                        if (ok instanceof Boolean && (Boolean) ok) {
                            clicked = true;
                            DashboardManager.log("[STAGE] clicked via JS MouseEvent dispatch fallback.");
                        }
                    }
                } catch (Exception e) {
                    DashboardManager.log("[STAGE] JS MouseEvent fallback error: " + e.getMessage());
                }
            }

            // If still not clicked, dump and fail early
            if (!clicked) {
                DashboardManager.log("[STAGE] Partner CTA could not be clicked after multiple strategies. Dumping CTA candidates for offline inspection.");
                try {
                    java.nio.file.Path dir = java.nio.file.Paths.get("target/debug/partner-cta-failure");
                    java.nio.file.Files.createDirectories(dir);
                    // page snapshot + html
                    page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png")));
                    java.nio.file.Files.writeString(dir.resolve("page.html"), page.content());

                    // small JS dump of matching nodes
                    try {
                        Object dump = page.evaluate(
                                "() => {" +
                                        "  const out = [];" +
                                        "  const nodes = Array.from(document.querySelectorAll('div,button,span'));" +
                                        "  for (const n of nodes.slice(0,300)) {" +
                                        "    try { const t = (n.textContent||'').trim(); if (t && /PARTNER\\s*(SHORTLISTED|ALIGNMENT)/i.test(t)) { out.push({text: t.slice(0,200), outer: (n.outerHTML||'').slice(0,2000), visible: !!(n.offsetParent !== null)}); } } catch(e){}" +
                                        "  }" +
                                        "  return out;" +
                                        "}"
                        );
                        if (dump != null) java.nio.file.Files.writeString(dir.resolve("dump.json"), dump.toString());
                    } catch (Exception je) {
                        DashboardManager.log("[STAGE] failed to produce JS dump: " + je.getMessage());
                    }
                } catch (Exception ignored) {}
                return false;
            }

            // Small wait for dialog/form to appear
            page.waitForTimeout(300);

            // --- Poll for inputs (dialog or inline) ---
            Locator dialog = page.locator("div.MuiDialog-container, div[role='dialog']").first();
            Locator root = (dialog != null && dialog.count() > 0 && dialog.isVisible()) ? dialog : page.locator("body");

            Locator vendorInput = null;
            Locator notesInput = null;
            Locator currencyInput = null;
            Locator amountInput = null;
            Locator finalBtnLocator = null;

            boolean inputsFound = false;
            int attempts = 12;
            for (int a = 0; a < attempts; a++) {
                try {
                    dialog = page.locator("div.MuiDialog-container, div[role='dialog']").first();
                    root = (dialog != null && dialog.count() > 0 && dialog.isVisible()) ? dialog : page.locator("body");

                    vendorInput = root.locator("input#selected_vendor_id, input[id='selected_vendor_id'], input[role='combobox'][aria-autocomplete]");
                    notesInput = root.locator("textarea[name='notes'], input[name='notes'], input[placeholder*='Notes']");
                    currencyInput = root.locator("input#vendor_currency, input[id='vendor_currency'], input[placeholder*='Vendor Currency']");
                    amountInput = root.locator("input[name='vendor_amount'], input[placeholder*='Vendor Amount'], input[placeholder*='Enter Vendor Amount']");
                    finalBtnLocator = root.locator("button:has-text('Move To Partner Shortlisting'), button:has-text('Move To Partner Alignment'), button:has-text('Move To Partner')");

                    // also try global search (inline)
                    if (vendorInput.count() == 0) vendorInput = page.locator("input#selected_vendor_id, input[id='selected_vendor_id'], input[role='combobox'][aria-autocomplete']");
                    if (notesInput.count() == 0) notesInput = page.locator("textarea[name='notes'], input[name='notes'], input[placeholder*='Notes']");
                    if (currencyInput.count() == 0) currencyInput = page.locator("input#vendor_currency, input[id='vendor_currency'], input[placeholder*='Vendor Currency']");
                    if (amountInput.count() == 0) amountInput = page.locator("input[name='vendor_amount'], input[placeholder*='Vendor Amount'], input[placeholder*='Enter Vendor Amount']");
                    if (finalBtnLocator.count() == 0) finalBtnLocator = page.locator("button:has-text('Move To Partner Shortlisting'), button:has-text('Move To Partner Alignment'), button:has-text('Move To Partner')");

                    int foundCount = 0;
                    if (vendorInput.count() > 0) foundCount++;
                    if (notesInput.count() > 0) foundCount++;
                    if (currencyInput.count() > 0) foundCount++;
                    if (amountInput.count() > 0) foundCount++;
                    if (finalBtnLocator.count() > 0) foundCount++;

                    if (foundCount > 0) {
                        inputsFound = true;
                        DashboardManager.log("[STAGE] Found inputs after CTA: vendor=" + vendorInput.count() + " notes=" + notesInput.count() + " currency=" + currencyInput.count() + " amount=" + amountInput.count() + " finalBtn=" + finalBtnLocator.count());
                        break;
                    }
                } catch (Exception ignored) {}

                // Recovery: re-click CTA once after some attempts (UI sometimes needs an extra click)
                if (a == 4 && !inputsFound) {
                    try {
                        DashboardManager.log("[STAGE] re-clicking CTA as a recovery attempt (attempt " + (a+1) + ")");
                        clickElementByTexts(new String[] {"xpath=//div[contains(translate(normalize-space(string(.)), '\u00A0',' '), 'PARTNER SHORTLISTED')]" }, "partner shortlisted", 800);
                    } catch (Exception ignored) {}
                }
                page.waitForTimeout(300);
            }

            if (!inputsFound) {
                DashboardManager.log("[STAGE] No form inputs detected after clicking Partner Shortlisted.");
                try {
                    java.nio.file.Path dir = java.nio.file.Paths.get("target/debug/partner-shortlisted-stage-failure");
                    java.nio.file.Files.createDirectories(dir);
                    page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png")));
                    java.nio.file.Files.writeString(dir.resolve("page.html"), page.content());
                } catch (Exception ignored) {}
                return false;
            }

            // Re-resolve root (dialog may now be present)
            dialog = page.locator("div.MuiDialog-container, div[role='dialog']").first();
            root = (dialog != null && dialog.count() > 0 && dialog.isVisible()) ? dialog : page.locator("body");

            // --- Fill vendor (autocomplete) ---
            try {
                Locator vInput = (root.locator("input#selected_vendor_id").count() > 0) ? root.locator("input#selected_vendor_id").first()
                        : (page.locator("input#selected_vendor_id").count() > 0 ? page.locator("input#selected_vendor_id").first() : null);
                if (vInput != null) {
                    vInput.scrollIntoViewIfNeeded();
                    try { vInput.fill(""); } catch (Exception ignored) {}
                    vInput.click();
                    if (vendorName != null && !vendorName.isEmpty()) {
                        vInput.type(vendorName, new Locator.TypeOptions().setDelay(30));
                        page.waitForTimeout(300);
                    }
                    boolean picked = pickFirstAutocompleteOption(vInput);
                    if (!picked) {
                        try { vInput.press("ArrowDown"); vInput.press("Enter"); page.waitForTimeout(250); } catch (Exception ignored) {}
                    }
                } else {
                    DashboardManager.log("[STAGE] Vendor input not found inside dialog/section.");
                }
            } catch (Exception e) {
                DashboardManager.log("[STAGE] vendor selection error: " + e.getMessage());
            }

            page.waitForTimeout(250);

            // --- Notes ---
            try {
                Locator nInput = root.locator("textarea[name='notes'], input[name='notes'], input[placeholder*='Notes']").first();
                if (nInput != null && nInput.count() > 0) {
                    nInput.scrollIntoViewIfNeeded();
                    try { nInput.fill(""); } catch (Exception ignored) {}
                    nInput.type(notes != null ? notes : "Automated note", new Locator.TypeOptions().setDelay(12));
                    page.waitForTimeout(120);
                } else {
                    DashboardManager.log("[STAGE] notes input not found.");
                }
            } catch (Exception e) {
                DashboardManager.log("[STAGE] notes input error: " + e.getMessage());
            }

            // --- Currency ---
            try {
                Locator curInput = root.locator("input#vendor_currency, input[id='vendor_currency'], input[placeholder*='Vendor Currency']").first();
                if (curInput != null && curInput.count() > 0) {
                    curInput.scrollIntoViewIfNeeded();
                    try { curInput.fill(""); } catch (Exception ignored) {}
                    curInput.click();
                    if (currency != null) curInput.type(currency, new Locator.TypeOptions().setDelay(20));
                    page.waitForTimeout(220);
                    boolean ok = pickFirstAutocompleteOption(curInput);
                    if (!ok) { try { curInput.press("ArrowDown"); curInput.press("Enter"); page.waitForTimeout(120); } catch (Exception ignored) {} }
                } else {
                    DashboardManager.log("[STAGE] vendor currency input not found.");
                }
            } catch (Exception e) {
                DashboardManager.log("[STAGE] vendor currency error: " + e.getMessage());
            }

            // --- Amount ---
            try {
                Locator amtInput = root.locator("input[name='vendor_amount'], input[placeholder*='Vendor Amount']").first();
                if (amtInput != null && amtInput.count() > 0) {
                    amtInput.scrollIntoViewIfNeeded();
                    try { amtInput.fill(""); } catch (Exception ignored) {}
                    amtInput.type(amount != null ? amount : "0", new Locator.TypeOptions().setDelay(10));
                    page.waitForTimeout(120);
                } else {
                    DashboardManager.log("[STAGE] vendor amount input not found.");
                }
            } catch (Exception e) {
                DashboardManager.log("[STAGE] vendor amount error: " + e.getMessage());
            }

            page.waitForTimeout(250);

            // --- Final CTA click ---
            boolean finalClicked = false;
            String[] finalSelectors = new String[] {
                    "button:has-text('Move To Partner Shortlisting')",
                    "button:has-text('Move To Partner Alignment')",
                    "button:has-text('Move To Partner')",
                    "button[type='submit']"
            };

            for (String sel : finalSelectors) {
                try {
                    Locator fb = root.locator(sel).first();
                    if (fb != null && fb.count() > 0) {
                        if (tryClick(fb, "final:" + sel)) {
                            page.waitForTimeout(500);
                            finalClicked = true;
                            DashboardManager.log("[STAGE] final CTA clicked via locator: " + sel);
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }

            // final JS fallback for Final CTA
            if (!finalClicked) {
                try {
                    Object res = page.evaluate(
                            "() => { const btn = Array.from(document.querySelectorAll('button,div[role=button]')).find(b => (b.textContent||'').trim().match(/Move\\s*To\\s*Partner\\s*Shortlisting|Move\\s*To\\s*Partner/i)); if (btn){ btn.scrollIntoView({block:'center'}); const ev = new MouseEvent('click', {bubbles:true, cancelable:true}); btn.dispatchEvent(ev); return true; } return false; }"
                    );
                    if (res instanceof Boolean && (Boolean) res) {
                        finalClicked = true;
                        page.waitForTimeout(500);
                        DashboardManager.log("[STAGE] final CTA clicked via JS fallback.");
                    }
                } catch (Exception e) {
                    DashboardManager.log("[STAGE] final CTA JS fallback error: " + e.getMessage());
                }
            }

            if (!finalClicked) {
                DashboardManager.log("[STAGE] final CTA click attempts failed.");
                try {
                    java.nio.file.Path dir = java.nio.file.Paths.get("target/debug/partner-shortlist-final-click-failed");
                    java.nio.file.Files.createDirectories(dir);
                    page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png")));
                    java.nio.file.Files.writeString(dir.resolve("page.html"), page.content());
                } catch (Exception ignored) {}
                return false;
            }

            page.waitForTimeout(800);
            DashboardManager.log("[STAGE] Partner Shortlisted stage completed!");
            return true;

        } catch (Exception e) {
            DashboardManager.log("[STAGE] Partner shortlisting error: " + e.getMessage());
            try {
                java.nio.file.Path dir = java.nio.file.Paths.get("target/debug/partner-shortlist-exception");
                java.nio.file.Files.createDirectories(dir);
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("page.png")));
                java.nio.file.Files.writeString(dir.resolve("page.html"), page.content());
            } catch (Exception ignored) {}
            return false;
        }
    }
    /**
     * Orchestrator: run Partner Shortlisted stage, then KYC & Documentation.
     * Keeps partnerShortlisting unchanged and calls KycDocumentationPage.
     *
     * Returns true if both partnerShortlisting and KYC flow succeed.
     */
    public boolean partnerShortlistThenKyc(
            String vendorName,
            String notes,
            String currency,
            String amount,
            String clientContractPath,
            String clientStartDdMmYyyy,
            String clientEndDdMmYyyy,
            String vendorContractPath,
            String vendorStartDdMmYyyy,
            String vendorEndDdMmYyyy,
            String kycNotes,
            String dealNameToFindInFinance
    ) {
        try {
            // 1) Run existing partnerShortlisting stage
            boolean psOk = partnerShortlisting(vendorName, notes, currency, amount);
            if (!psOk) {
                DashboardManager.log("[FLOW] partnerShortlisting failed; aborting KYC step.");
                return false;
            }

            // 2) Call KYC & Documentation page helper
            try {
                KycDocumentationPage kyc = new KycDocumentationPage(page);

                boolean kycOk = kyc.completeKycAndDocumentation(
                        clientContractPath,
                        clientStartDdMmYyyy,
                        clientEndDdMmYyyy,
                        vendorContractPath,
                        vendorStartDdMmYyyy,
                        vendorEndDdMmYyyy,
                        kycNotes,
                        dealNameToFindInFinance
                );

                if (!kycOk) {
                    DashboardManager.log("[FLOW] KYC & Documentation stage FAILED — check target/debug for snapshots.");
                    return false;
                } else {
                    DashboardManager.log("[FLOW] KYC & Documentation stage completed successfully.");
                    return true;
                }
            } catch (Exception e) {
                DashboardManager.log("[FLOW] Exception while running KYC flow: " + e.getMessage());
                return false;
            }
        } catch (Exception ex) {
            DashboardManager.log("[FLOW] partnerShortlistThenKyc top-level error: " + ex.getMessage());
            return false;
        }
    }

}
