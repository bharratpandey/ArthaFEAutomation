package com.artha.utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;

public class DashboardManager {

    private static ExtentReports extent;
    private static ExtentTest currentTest;
    public static final String REPORT_PATH = "target/AutomationDashboard.html";

    private static int passCount = 0;
    private static int failCount = 0;
    private static int infoCount  = 0;

    public static void initReport() {
        if (extent == null) {
            ExtentSparkReporter spark = new ExtentSparkReporter(REPORT_PATH);
            spark.config().setTheme(Theme.DARK);
            spark.config().setDocumentTitle("Artha Test Dashboard");
            spark.config().setReportName("E2E Automation Flow Execution");

            extent = new ExtentReports();
            extent.attachReporter(spark);
            extent.setSystemInfo("Environment", "DEV");
            extent.setSystemInfo("Tester", "Artha Automation");
        }
    }

    public static void startTest(String testName) {
        if (extent != null) {
            currentTest = extent.createTest(testName);
            passCount = 0;
            failCount = 0;
            infoCount = 0;
        }
    }

    public static void log(String message) {
        // 1. Console Output
        System.out.println(message);

        // 2. ExtentReports Logic (HTML)
        if (currentTest != null) {
            if (message.contains("✅") || message.contains("SUCCESS")) {
                currentTest.pass(message);
                passCount++;
            } else if (message.contains("❌") || message.contains("Failed")) {
                currentTest.fail(message);
                failCount++;
            } else if (message.contains("⚠️")) {
                currentTest.warning(message);
                infoCount++;
            } else {
                currentTest.info(message);
                infoCount++;
            }
        }
    }

    public static void flushReport() {
        if (extent != null) {
            extent.flush();
        }
    }

    public static int getPassCount() { return passCount; }
    public static int getFailCount() { return failCount; }
    public static int getInfoCount() { return infoCount; }
}