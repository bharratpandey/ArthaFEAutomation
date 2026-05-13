package com.artha.utils;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import java.io.File;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public class EmailSender {

    public static void sendDashboardEmail(String toEmail) {
        DashboardManager.log("\n📧 Preparing to send Artha Automation Dashboard via Email...");

        final String fromEmail = "bharat.pandeyltd@gmail.com";
        String envPassword = System.getenv("EMAIL_PASSWORD");
        final String appPassword = (envPassword == null || envPassword.isEmpty())
                ? "boecqfqfmrjebbjq"
                : envPassword;

        // Dynamic IST time
        String istTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a 'IST'"));

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(fromEmail, appPassword);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject("🚀 Artha Automation: Daily Execution Report");

            int passed = DashboardManager.getPassCount();
            int failed = DashboardManager.getFailCount();

            String statusBanner = (failed > 0)
                    ? "⚠️ Execution Completed with Failures"
                    : "✅ Execution Completed Successfully";
            String bannerColor = (failed > 0) ? "#f8d7da" : "#e8f5e9";
            String textColor   = (failed > 0) ? "#721c24" : "#2e7d32";

            String htmlBody = """
<!DOCTYPE html>
<html>
<head>
    <style>
        body { font-family: 'Segoe UI', Tahoma, sans-serif; background-color: #f4f7f6; margin: 0; padding: 20px; }
        .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 15px rgba(0,0,0,0.1); border: 1px solid #e1e1e2; }
        .header { background: linear-gradient(135deg, #6022C3, #8E7CC3); padding: 25px; text-align: center; color: white; }
        .content { padding: 30px; color: #333; line-height: 1.6; }
        .status-banner { background-color: %s; color: %s; padding: 15px; text-align: center; font-weight: bold; border-radius: 8px; margin-bottom: 20px; font-size: 18px; }
        .stats-container { display: flex; justify-content: space-around; margin: 20px 0; padding: 15px; background: #f9f9f9; border-radius: 8px; text-align: center; }
        .stat-box { flex: 1; }
        .stat-number { font-size: 24px; font-weight: bold; display: block; }
        .stat-label { font-size: 12px; text-transform: uppercase; color: #666; }
        .details-table { width: 100%%; border-collapse: collapse; margin-top: 10px; }
        .details-table th, .details-table td { padding: 12px; text-align: left; border-bottom: 1px solid #eee; font-size: 14px; }
        .details-table th { color: #6022C3; width: 40%%; }
        .footer { background: #f1f1f1; padding: 15px; text-align: center; font-size: 11px; color: #888; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header"><h2>Artha E2E Automation Report</h2></div>
        <div class="content">
            <p>Hello Team,</p>
            <div class="status-banner">%s</div>

            <div class="stats-container">
                <div class="stat-box">
                    <span class="stat-number" style="color: #28a745;">%d</span>
                    <span class="stat-label">Passed</span>
                </div>
                <div class="stat-box" style="border-left: 1px solid #ddd; border-right: 1px solid #ddd;">
                    <span class="stat-number" style="color: #dc3545;">%d</span>
                    <span class="stat-label">Failed</span>
                </div>
            </div>

            <table class="details-table">
                <tr><th>Environment</th><td>DEV</td></tr>
                <tr><th>Execution Time</th><td>%s</td></tr>
            </table>
            <p style="margin-top:25px; font-size: 13px; color: #555;">Detailed execution logs and screenshots are available in the attached <b>HTML Dashboard</b>.</p>
        </div>
        <div class="footer">Automated by Artha Bot via GitHub Actions</div>
    </div>
</body>
</html>
""".formatted(
                    bannerColor, textColor,
                    statusBanner,
                    passed, failed,
                    istTime
            );

            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setContent(htmlBody, "text/html; charset=utf-8");

            BodyPart attachmentPart = new MimeBodyPart();
            File file = new File(DashboardManager.REPORT_PATH);
            if (file.exists()) {
                attachmentPart.setDataHandler(new DataHandler(new FileDataSource(file)));
                attachmentPart.setFileName("Artha_Automation_Dashboard.html");
            }

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);
            if (file.exists()) multipart.addBodyPart(attachmentPart);

            message.setContent(multipart);
            Transport.send(message);
            DashboardManager.log("✅ Artha Dashboard Email sent at: " + istTime);

        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}