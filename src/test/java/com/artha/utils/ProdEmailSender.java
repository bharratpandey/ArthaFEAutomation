package com.artha.utils;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.io.File;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * ProdEmailSender
 *
 * Identical HTML styling to {@link EmailSender} but:
 *   - Subject  : "Artha PROD Health Check — &lt;PASSED/FAILED&gt;"
 *   - Environment row : "PRODUCTION — artha.emb.global"
 *   - Attachment label: "Artha_Prod_Health_Dashboard.html"
 *
 * Does NOT touch DashboardManager pass/fail counters — it only reads them.
 * This keeps the existing EmailSender (used by other tests) completely unchanged.
 */
public class ProdEmailSender {

    private static final String FROM_EMAIL = "bharat.pandeyltd@gmail.com";

    private static String appPassword() {
        String env = System.getenv("EMAIL_PASSWORD");
        return (env == null || env.isEmpty()) ? "joolfzckxmlguwnl" : env;
    }

    /**
     * Send the health-check summary email.
     *
     * @param toEmail        recipient address (may be comma-separated)
     * @param slowCallCount  number of APIs that took > 1000ms (informational only)
     */
    public static void sendHealthCheckEmail(String toEmail, long slowCallCount) {
        DashboardManager.log("\n📧 Preparing Artha PROD Health Check email...");

        String istTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a 'IST'"));

        int passed = DashboardManager.getPassCount();
        int failed = DashboardManager.getFailCount();

        // Only real assertion failures (non-2xx responses that are NOT the
        // expected 400s on approval pages) count as failures.
        // Slow APIs are timing warnings — they never increment failCount
        // because we switched their label from ❌ to 🐢.
        boolean hasRealFailure = failed > 0;

        String statusBanner = hasRealFailure
                ? "⚠️ Health Check Completed with Failures"
                : "✅ Health Check Passed — All 46 Steps OK";
        String bannerColor  = hasRealFailure ? "#f8d7da" : "#e8f5e9";
        String textColor    = hasRealFailure ? "#721c24" : "#2e7d32";
        String subject      = hasRealFailure
                ? "❌ Artha PROD Health Check — FAILED — " + istTime
                : "✅ Artha PROD Health Check — PASSED — " + istTime;

        String htmlBody = """
<!DOCTYPE html>
<html>
<head>
  <style>
    body { font-family: 'Segoe UI', Tahoma, sans-serif; background-color: #f4f7f6;
           margin: 0; padding: 20px; }
    .container { max-width: 620px; margin: 0 auto; background: #ffffff;
                 border-radius: 12px; overflow: hidden;
                 box-shadow: 0 4px 15px rgba(0,0,0,0.1);
                 border: 1px solid #e1e1e2; }
    .header { background: linear-gradient(135deg, #1a6b3a, #27ae60);
              padding: 25px; text-align: center; color: white; }
    .content { padding: 30px; color: #333; line-height: 1.6; }
    .status-banner { background-color: %s; color: %s; padding: 15px;
                     text-align: center; font-weight: bold;
                     border-radius: 8px; margin-bottom: 20px; font-size: 18px; }
    .stats-container { display: flex; justify-content: space-around; margin: 20px 0;
                       padding: 15px; background: #f9f9f9;
                       border-radius: 8px; text-align: center; }
    .stat-box   { flex: 1; }
    .stat-number { font-size: 26px; font-weight: bold; display: block; }
    .stat-label  { font-size: 12px; text-transform: uppercase; color: #666; }
    .details-table { width: 100%%; border-collapse: collapse; margin-top: 10px; }
    .details-table th, .details-table td {
        padding: 12px; text-align: left;
        border-bottom: 1px solid #eee; font-size: 14px; }
    .details-table th { color: #1a6b3a; width: 40%%; }
    .note { background: #fff8e1; border-left: 4px solid #ffc107;
            padding: 10px 14px; margin-top: 16px;
            font-size: 13px; color: #555; border-radius: 4px; }
    .footer { background: #f1f1f1; padding: 15px; text-align: center;
              font-size: 11px; color: #888; }
  </style>
</head>
<body>
<div class="container">
  <div class="header"><h2>Artha / EMB Prod Health Check</h2></div>
  <div class="content">
    <p>Hello Team,</p>
    <div class="status-banner">%s</div>

    <div class="stats-container">
      <div class="stat-box">
        <span class="stat-number" style="color:#28a745">%d</span>
        <span class="stat-label">Cases Passed</span>
      </div>
      <div class="stat-box" style="border-left:1px solid #ddd;border-right:1px solid #ddd">
        <span class="stat-number" style="color:#dc3545">%d</span>
        <span class="stat-label">Cases Failed</span>
      </div>
      <div class="stat-box">
        <span class="stat-number" style="color:#17a2b8">46</span>
        <span class="stat-label">Total Modules</span>
      </div>
    </div>

    <table class="details-table">
      <tr><th>Environment</th><td><b>PRODUCTION</b> — artha.emb.global</td></tr>
      <tr><th>Execution Time</th><td>%s</td></tr>
      <tr><th>Slow APIs (&gt;1000ms)</th>
          <td style="color:%s"><b>%d</b> %s</td></tr>
      <tr><th>Trace file</th><td>artha-prod-health-trace.zip (view at trace.playwright.dev)</td></tr>
    </table>

    <div class="note">
      ℹ️  <b>Known expected HTTP 400s:</b> Steps 20, 21 and 22
      (/poapproval, /hodescapproval, /escapproval) — this account has no
      access to those approval modules. These are logged as ✅ PASSED and
      do <b>not</b> contribute to the failure count.
    </div>

    <p style="margin-top:25px; font-size:13px; color:#555;">
      Full execution logs and screenshots are in the attached
      <b>HTML Dashboard</b>.
    </p>
  </div>
  <div class="footer">Automated by Artha Health Check Bot · GitHub Actions · EMB</div>
</div>
</body>
</html>
""".formatted(
                bannerColor, textColor,
                statusBanner,
                passed, failed,
                istTime,
                slowCallCount > 0 ? "#856404" : "#2e7d32",
                slowCallCount,
                slowCallCount > 0 ? "⚠️ (informational — slow ≠ error)" : "✅ None");

        // ── SMTP send ──────────────────────────────────────────────────────
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(FROM_EMAIL, appPassword());
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(FROM_EMAIL));
            message.setRecipients(Message.RecipientType.TO,
                    InternetAddress.parse(toEmail));
            message.setSubject(subject);

            // HTML body
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=utf-8");

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(htmlPart);

            // Attach the ExtentReports dashboard
            File reportFile = new File(DashboardManager.REPORT_PATH);
            if (reportFile.exists()) {
                MimeBodyPart attach = new MimeBodyPart();
                attach.setDataHandler(new DataHandler(new FileDataSource(reportFile)));
                attach.setFileName("Artha_Prod_Health_Dashboard.html");
                multipart.addBodyPart(attach);
            }

            message.setContent(multipart);
            Transport.send(message);

            DashboardManager.log("✅ Health Check email sent to " + toEmail
                    + " at " + istTime);

        } catch (MessagingException e) {
            DashboardManager.log("❌ Email send failed: " + e.getMessage());
        }
    }
}