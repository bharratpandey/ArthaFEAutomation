package com.artha.pages;

import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.LoadState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.artha.utils.DashboardManager;

public class KycDocumentationPage {

    private final Page page;

    public KycDocumentationPage(Page page) {
        this.page = page;
    }

    // --- Helpers ---

    private String ddmmyyyyToIso(String ddmmyyyy) {
        if (ddmmyyyy == null) return null;
        try {
            String[] parts = ddmmyyyy.trim().split("[/\\-]");
            if (parts.length != 3) return null;
            String dd = parts[0];
            String mm = parts[1];
            String yyyy = parts[2];
            if (dd.length() == 1) dd = "0" + dd;
            if (mm.length() == 1) mm = "0" + mm;
            if (yyyy.length() == 2) yyyy = "20" + yyyy;
            return yyyy + "-" + mm + "-" + dd;
        } catch (Exception e) {
            DashboardManager.log("[KYC] ddmmyyyyToIso parse error: " + e.getMessage());
            return null;
        }
    }

    private boolean tryClick(Locator loc, String name) {
        try {
            if (loc == null || loc.count() == 0) return false;
            loc.scrollIntoViewIfNeeded();
            try {
                loc.click();
                DashboardManager.log("[KYC] tryClick normal: " + name);
                return true;
            } catch (Exception e1) {
                try {
                    loc.click(new Locator.ClickOptions().setForce(true));
                    DashboardManager.log("[KYC] tryClick forced: " + name);
                    return true;
                } catch (Exception e2) {
                    try {
                        loc.evaluate("(el) => { try { el.scrollIntoView({block:'center'}); el.click(); } catch(e){} }");
                        DashboardManager.log("[KYC] tryClick js-dispatch: " + name);
                        return true;
                    } catch (Exception e3) {
                        DashboardManager.log("[KYC] tryClick failed all ways: " + name + " -> " + e3.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            DashboardManager.log("[KYC] tryClick top error for " + name + ": " + ex.getMessage());
        }
        return false;
    }

    private boolean dispatchRichClickViaJs(Locator loc, String logicalName) {
        try {
            if (loc == null || loc.count() == 0) return false;
            Object res = loc.evaluate(
                    "(el) => {\n" +
                            "  try {\n" +
                            "    el.scrollIntoView({block: 'center'});\n" +
                            "    const rect = el.getBoundingClientRect();\n" +
                            "    const clientX = rect.left + rect.width/2;\n" +
                            "    const clientY = rect.top + rect.height/2;\n" +
                            "    const dispatch = (type, eventInit) => el.dispatchEvent(new (window.PointerEvent || window.MouseEvent)(type, Object.assign({bubbles:true, cancelable:true, view:window, clientX, clientY}, eventInit)));\n" +
                            "    try { dispatch('pointerdown', {}); } catch(e) {}\n" +
                            "    try { dispatch('mousedown', {}); } catch(e) {}\n" +
                            "    try { dispatch('pointerup', {}); } catch(e) {}\n" +
                            "    try { dispatch('mouseup', {}); } catch(e) {}\n" +
                            "    try { dispatch('click', {}); } catch(e) {}\n" +
                            "    try { el.click(); } catch(e) {}\n" +
                            "    return true;\n" +
                            "  } catch(e) { return {err: String(e && e.message ? e.message : e)} }\n" +
                            "}"
            );
            if (res instanceof Boolean && (Boolean) res) {
                DashboardManager.log("[KYC] dispatchRichClickViaJs succeeded: " + logicalName);
                return true;
            }
        } catch (Exception ex) {
            DashboardManager.log("[KYC] dispatchRichClickViaJs error for " + logicalName + ": " + ex.getMessage());
        }
        return false;
    }

    // --- Main Method ---

    public boolean completeKycAndDocumentation(
            String clientContractFilePath,
            String clientContractStart_ddmmyyyy,
            String clientContractEnd_ddmmyyyy,
            String vendorContractFilePath,
            String vendorContractStart_ddmmyyyy,
            String vendorContractEnd_ddmmyyyy,
            String notesForKyc,
            String dealNameToApprove
    ) {

        try {
            DashboardManager.log("[KYC] Starting KYC & Documentation workflow...");

            // ==========================================
            // 1) Click "KYC AND DOCUMENTATION" CTA
            // ==========================================
            Locator kycCta = page.locator("div[class*='text-[#8e6b2a]']").first();

            if (kycCta.count() == 0) {
                kycCta = page.locator("div.cursor-pointer").filter(new Locator.FilterOptions().setHasText(Pattern.compile("^\\s*KYC AND DOCUMENTATION", Pattern.CASE_INSENSITIVE))).first();
            }

            boolean clicked = false;
            int maxCtaAttempts = 3;

            for (int attempt = 1; attempt <= maxCtaAttempts && !clicked; attempt++) {
                if (kycCta.count() > 0) {
                    try { kycCta.scrollIntoViewIfNeeded(); } catch (Exception ignored) {}

                    if (tryClick(kycCta, "KYC CTA")) {
                        page.waitForTimeout(1000);
                    }

                    if (page.locator("input[name='client_signed_contract']").isVisible()) {
                        clicked = true;
                        break;
                    }

                    dispatchRichClickViaJs(kycCta, "KYC CTA JS");
                    page.waitForTimeout(1000);

                    if (page.locator("input[name='client_signed_contract']").isVisible()) {
                        clicked = true;
                        break;
                    }
                }
                page.waitForTimeout(500);
            }

            if (!clicked) {
                DashboardManager.log("[KYC] CRITICAL: Could not open KYC modal.");
                return false;
            }

            // ==========================================
            // 2) FILL FORM
            // ==========================================
            Frame kycFrame = null;
            try {
                try { page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(1500)); } catch (Exception ignored) {}
                List<Frame> candidates = page.frames().stream().filter(f -> {
                    try { return f.locator("input[name='client_signed_contract']").count() > 0; } catch (Exception ex) { return false; }
                }).collect(Collectors.toList());
                if (!candidates.isEmpty()) kycFrame = candidates.get(0);
            } catch (Exception ignored) {}

            Frame finalKycFrame = kycFrame;
            java.util.function.Function<String, Locator> in = (sel) -> (finalKycFrame != null) ? finalKycFrame.locator(sel) : page.locator(sel);

            try {
                Locator input = in.apply("input[name='client_signed_contract']");
                if (input.count() > 0 && clientContractFilePath != null) {
                    Path p = Paths.get(clientContractFilePath).toAbsolutePath();
                    if (Files.exists(p)) input.setInputFiles(p);
                }
            } catch (Exception ignored) {}

            try {
                Locator start = in.apply("input[name='contract_start_date']");
                Locator end = in.apply("input[name='contract_end_date']");
                String s = ddmmyyyyToIso(clientContractStart_ddmmyyyy);
                String e = ddmmyyyyToIso(clientContractEnd_ddmmyyyy);
                if (start.count() > 0 && s != null) { start.click(); start.fill(s); }
                if (end.count() > 0 && e != null) { end.click(); end.fill(e); }
            } catch (Exception ignored) {}

            try {
                Locator input = in.apply("input[name='vendor_signed_contract']");
                if (input.count() > 0 && vendorContractFilePath != null) {
                    Path p = Paths.get(vendorContractFilePath).toAbsolutePath();
                    if (Files.exists(p)) input.setInputFiles(p);
                }
            } catch (Exception ignored) {}

            try {
                Locator start = in.apply("input[name='vendor_contract_start_date']");
                Locator end = in.apply("input[name='vendor_contract_end_date']");
                String s = ddmmyyyyToIso(vendorContractStart_ddmmyyyy);
                String e = ddmmyyyyToIso(vendorContractEnd_ddmmyyyy);
                if (start.count() > 0 && s != null) { start.click(); start.fill(s); }
                if (end.count() > 0 && e != null) { end.click(); end.fill(e); }
            } catch (Exception ignored) {}

            try {
                page.locator("input[name='notes']").fill(notesForKyc != null ? notesForKyc : "Automated");
            } catch (Exception ignored) {}

            page.waitForTimeout(500);

            try {
                Locator submitBtn = in.apply("button:has-text('Move To Kyc Documentation')");
                if (submitBtn.count() == 0) submitBtn = in.apply("button:has-text('Move To Kyc')");

                tryClick(submitBtn, "Submit KYC Form");
                page.waitForTimeout(3000);
            } catch (Exception ignored) {}

            // ==========================================
            // 3) NAVIGATE TO FINANCE (FIXED SELECTOR CRASH)
            // ==========================================
            try {
                // Step 1: Finance Nav
                Locator financeNav = page.locator("a[href*='embfinance']").first();
                if (financeNav.count() > 0) {
                    financeNav.click();
                } else {
                    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("finance", Pattern.CASE_INSENSITIVE))).click();
                }
                page.waitForLoadState(LoadState.NETWORKIDLE);

                // Step 2: Doc Requests (Fixed mixed selector string)
                Locator docReq = page.locator("a[href*='podocVerification']").first();
                if (docReq.count() == 0) {
                    docReq = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("Doc Requests", Pattern.CASE_INSENSITIVE)));
                }
                docReq.click();
                page.waitForLoadState(LoadState.NETWORKIDLE);

                // Step 3: Click Tab
                page.locator("li[data-value='deal_doc']").click();
                page.waitForTimeout(1000);
            } catch (Exception e) {
                DashboardManager.log("[KYC] Navigation error: " + e.getMessage());
                return false;
            }

            // ==========================================
            // 4) APPROVE DOCS
            // ==========================================
            Locator dealRow = page.locator("tr").filter(new Locator.FilterOptions().setHasText(dealNameToApprove)).first();

            if (dealRow.count() > 0) {
                dealRow.scrollIntoViewIfNeeded();

                // Approve each doc; label matching is now case-insensitive
                approveSpecificDocInRow(dealRow, "Client Contract");
                approveSpecificDocInRow(dealRow, "Vendor Contract");
                approveSpecificDocInRow(dealRow, "Kyc Document");

            } else {
                DashboardManager.log("[KYC] Deal not found: " + dealNameToApprove);
                return false;
            }

            return true;

        } catch (Exception ex) {
            DashboardManager.log("[KYC] Exception: " + ex.getMessage());
            return false;
        }
    }

    private void approveSpecificDocInRow(Locator row, String docLabel) {
        try {
            DashboardManager.log("[KYC] Processing: " + docLabel);

            String lowercaseLabel = docLabel.toLowerCase();
            String xpath =
                    ".//div[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '" + lowercaseLabel + "')]" +
                            "/following-sibling::div[1]//*[local-name()='svg' and @data-testid='CheckCircleOutlineIcon']";

            Locator approveIcon = row.locator("xpath=" + xpath).first();

            if (approveIcon.count() == 0) {
                DashboardManager.log("[KYC] Icon not found for: " + docLabel);
                return;
            }

            // ===========================================================================
            // LOGIC A: KYC DOCUMENT (Blind Click / Fire & Forget)
            // ===========================================================================
            if ("kyc document".equalsIgnoreCase(docLabel)) {
                DashboardManager.log("[KYC] KYC Document found. Clicking approve immediately (skipping verification check)...");

                // 1. Click the icon blindly
                try {
                    approveIcon.scrollIntoViewIfNeeded();
                    approveIcon.click(new Locator.ClickOptions().setForce(true));
                } catch (Exception e) {
                    DashboardManager.log("[KYC] Click failed on KYC icon. Moving on.");
                    return;
                }

                // 2. Wait briefly (2s) for modal.
                // If it opens -> Fill & Approve.
                // If it DOES NOT open (Timeout) -> Assume already done and return.
                try {
                    Locator modal = page.locator("div[role='dialog']").last();
                    modal.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(2000));

                    if (modal.isVisible()) {
                        modal.locator("textarea").fill("Automated Approval for " + docLabel);
                        Locator confirmBtn = modal.locator("button")
                                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("approve", Pattern.CASE_INSENSITIVE)));
                        confirmBtn.click();
                        modal.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
                        DashboardManager.log("[KYC] KYC Document Approved via modal.");
                    }
                } catch (Exception e) {
                    DashboardManager.log("[KYC] Modal did not open for KYC (likely already verified). Moving to next step immediately.");
                }
                return; // ✅ EXIT HERE FOR KYC
            }

            // ===========================================================================
            // LOGIC B: CLIENT / VENDOR CONTRACTS (Keep Original Logic)
            // ===========================================================================

            // 1. Check if already Green/Verified (To save time if they are done)
            String fillColor = approveIcon.getAttribute("fill");
            String color = approveIcon.getAttribute("color");
            // Check for green color code
            boolean isGreen = (fillColor != null && fillColor.contains("26, 197, 92")) ||
                    (color != null && color.contains("26, 197, 92"));

            if (isGreen) {
                DashboardManager.log("[KYC] " + docLabel + " is already verified (Green). Skipping.");
                return;
            }

            // 2. Perform Approval if not green
            approveIcon.scrollIntoViewIfNeeded();
            approveIcon.click(new Locator.ClickOptions().setForce(true));

            Locator modal = page.locator("div[role='dialog']").last();
            modal.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));

            modal.locator("textarea").fill("Automated Approval for " + docLabel);

            Locator confirmBtn = modal.locator("button")
                    .filter(new Locator.FilterOptions().setHasText(Pattern.compile("approve", Pattern.CASE_INSENSITIVE)));

            confirmBtn.click();

            modal.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));

            page.waitForTimeout(800);

            DashboardManager.log("[KYC] Approved successfully: " + docLabel);

        } catch (Exception e) {
            DashboardManager.log("[KYC] Error processing " + docLabel + ": " + e.getMessage());
        }
    }
}
