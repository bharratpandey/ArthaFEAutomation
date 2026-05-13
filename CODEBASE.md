# ArthaFEAutomation — Codebase & Architecture Reference

## Overview

End-to-end UI automation suite for the **EMB (Artha) platform** — a deal management, project tracking, and finance portal. Tests are written in Java 17 with Microsoft Playwright and JUnit 5, following the **Page Object Model** pattern.

---

## Tech Stack

Layer

Library

Version

Browser automation

Microsoft Playwright

1.49.0

Test runner

JUnit Jupiter

5.10.1

Build / dependency

Maven

—

Reporting

Allure JUnit5

2.29.0

Java

JDK

17

---

## Commands

```bash
# Install Playwright browsers (once after clone)
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install"

# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=DealTest
mvn test -Dtest=AccountTest
mvn test -Dtest=PMTProjectTest
mvn test -Dtest=LoginTest

# Run a single test method
mvn test -Dtest=DealTest#createDealHappyPath

# Compile test sources without running
mvn test-compile

# Generate Allure HTML report (after tests have run)
mvn allure:report

# Open Allure report in browser live
mvn allure:serve
```

All tests run against live **dev** environments. No local mock servers. Browser launches in **headed mode** (`setHeadless(false)`) always.

---

## Package Structure

```
src/test/java/com/artha/
├── tests/           # JUnit test classes (orchestration only)
│   ├── BaseTest.java
│   ├── LoginTest.java
│   ├── AccountTest.java
│   ├── DealTest.java
│   └── PMTProjectTest.java
└── pages/           # Page Object classes (all UI interaction)
    ├── LoginPage.java
    ├── AccountPage.java
    ├── DealPage.java
    ├── SupplyPage.java
    ├── StagePage.java
    ├── KycDocumentationPage.java
    ├── DealClosedWonPage.java
    ├── DealProjectCreatedPage.java
    ├── DealPICreationPage.java
    ├── ProjectMilestonePage.java
    ├── ProjectPICreation.java
    ├── ProjectPOCreation.java
    ├── PMTProjectCreation.java
    ├── PMTMilestonePage.java
    ├── PMTPICreation.java
    ├── PMTPOCreation.java
    ├── PMTCreditNote.java
    ├── PMTDebitNote.java
    ├── EscrowTransferUptoAccount.java
    ├── EscrowTransferUptoProject.java
    └── EscrowTransferUptoSubProject.java

src/test/resources/
├── demo-client.pdf           # Client contract upload fixture
├── demo-client-signoff.pdf   # Client sign-off fixture
├── demo-proposal.pdf         # BA proposal upload fixture
├── demo-vendor.pdf           # Vendor contract upload fixture
└── testing.xml               # TestNG-style XML (legacy, not used by Surefire)
```

---

## Target Applications

Portal

URL

Used By

KAM (Key Account Management)

`https://dev-kam-v2.emb.global`

All test classes

Supply (vendor shortlisting)

`https://dev-supply.emb.global`

`DealTest` (second tab)

---

## Architecture

### BaseTest vs. Standalone Tests

**`BaseTest`** manages the Playwright lifecycle at class/method scope:

```
@BeforeAll  → create Playwright + Browser (shared across all @Test methods)
@BeforeEach → new BrowserContext + Page (isolated per test)
@AfterEach  → close Page + Context
@AfterAll   → close Browser + Playwright
```

`DealTest` and `LoginTest` **extend** `BaseTest` and inherit this lifecycle.

`AccountTest` and `PMTProjectTest` do **not** extend `BaseTest`. They each manage their own `Playwright` / `Browser` / `BrowserContext` / `Page` inside `@BeforeAll`/`@AfterAll` or `@BeforeEach`/`@AfterEach`. `PMTProjectTest` additionally sets `setSlowMo(500)` to pace all interactions.

---

### Test Flows

#### DealTest — Full End-to-End Deal Flow

The single `createDealHappyPath()` test covers the entire deal lifecycle across both portals:

```
1.  Login (KAM)
2.  Create Deal
3.  Move to Partner Alignment  (DealPage)
4.  Supply Portal: Login, search deal, shortlist vendor  (SupplyPage — second tab)
5.  Return to KAM tab
6.  Partner Shortlisted stage  (StagePage)
7.  KYC & Documentation        (KycDocumentationPage)
8.  Finance → Doc Requests     (inline navigation)
9.  Move to Closed Won         (DealClosedWonPage)
10. Move to Project Created    (DealProjectCreatedPage)
11. Create Project Milestones  (ProjectMilestonePage)
12. PI Creation + Finance flow (ProjectPICreation)
13. PO Creation + Finance flow (ProjectPOCreation)
14. Credit Note flow           (PMTCreditNote)
15. Debit Note flow            (PMTDebitNote)
16. Escrow transfer → Account  (EscrowTransferUptoAccount)
17. Escrow transfer → Project  (EscrowTransferUptoProject)
18. Escrow transfer → Sub-Project (EscrowTransferUptoSubProject)
```

**Multi-tab pattern**: Step 4 opens a second `Page` inside the same `BrowserContext`:

```java
Page supplyPage = page.context().newPage();
SupplyPage supply = new SupplyPage(supplyPage);
// ... supply actions ...
supply.close();
page.bringToFront();  // return focus to KAM tab
```

#### PMTProjectTest — PMT Project Standalone Flow

```
1.  Login + Create Project  (PMTProjectCreation)
2.  Create Milestones       (PMTMilestonePage)
3.  PI Creation + Finance   (PMTPICreation)
4.  PO Creation + Finance   (PMTPOCreation)
5.  Credit Note             (PMTCreditNote)
6.  Debit Note              (PMTDebitNote)
```

#### AccountTest — Account Creation

Standalone test (no `BaseTest`). Creates an account with configurable type (`Registered`, `Overseas`, `Unregistered`, `SEZ`). Generates random PAN / GSTIN. Handles the "contact exists" toast that conditionally disables first/last name fields.

#### LoginTest — Basic Login Verification

Verifies login succeeds and URL leaves `/login`. Saves debug artifacts on failure.

---

### Page Object Conventions

Every page class has the same constructor signature:

```java
public SomePage(Page page) {
    this.page = page;
}
```

Return types:

-   `boolean` — success/failure (used with `assertTrue`)
-   `String` / `List<String>` — captured values like IDs or names, used with `assertNotNull` / `assertFalse(list.isEmpty())`
-   `void` — setup/navigation steps that are not assertion points

---

### Resilience Patterns

The app is a React + MUI frontend. Standard Playwright `.fill()` and `.click()` often fail because React intercepts synthetic events. Every page class replicates one or more of these fallback chains:

#### `safeType(selector, value)`

1.  `locator.fill("") + locator.type(value)`
2.  JS: `el.value = v; el.dispatchEvent(new Event('input', {bubbles:true}))`
3.  JS direct property set without dispatch

#### `selectFirstAutocompleteOption(inputSelector, visibleText)`

1.  Type text into `input`, wait for `ul[role='listbox'] li[role='option']`
2.  Exact text match → partial match → first option (fallback)
3.  Keyboard `ArrowDown` + `Enter` if no list appears

#### `tryClick(locator, name)` (StagePage)

1.  `locator.click()`
2.  `locator.click(new ClickOptions().setForce(true))`
3.  JS: `el.style.pointerEvents='auto'; el.dispatchEvent(new MouseEvent('click', {bubbles:true}))`

#### `selectComboboxAndPickExact(inputSelector, desiredValue)` (AccountPage)

1.  Click, fill with desired value, wait up to 3s for suggestion list
2.  Exact case-insensitive match → contains match → keyboard fallback
3.  Works for MUI `Autocomplete` and standard `listbox` dropdowns

---

### Debug Artifacts

On failure, page classes write to `target/debug/<label>/`:

-   `page.png` — screenshot at point of failure
-   `page.html` — full page HTML
-   `dump.json` / `*.txt` — optional DOM inspection dumps

`PMTProjectTest` saves a success screenshot to `screenshots/full-project-flow-<name>.png`.

Both `target/debug/` and `screenshots/` are untracked (listed in `.gitignore`).

---

### Credentials

Hardcoded as `private static final String` constants in each test class. There is no properties file or environment variable mechanism.

Test Class

Portal

Email

`DealTest`

KAM

`Bharat.pandey@emb.global`

`DealTest`

Supply

`psnayal08@gmail.com`

`LoginTest`

KAM

`Bharat.pandey+1@emb.global`

`AccountTest`

KAM

`Bharat.pandey@emb.global`

`PMTProjectTest`

KAM

(inside `PMTProjectCreation`)

---

### File Uploads

PDF fixtures in `src/test/resources/` are referenced by relative path from project root:

```java
Paths.get("src/test/resources/demo-proposal.pdf")
```

Maven copies them to `target/test-classes/` during `test-compile`. The `input[type='file']` element receives them via `locator.setInputFiles(path)`.

---

### Deal Stage Machine

The deal progresses through stages, each requiring a specific page object:

```
NEW DEAL
  └─ DealPage.moveToPartnerAlignment()
       └─ [supply shortlists vendor]
           └─ StagePage.partnerShortlisting()
                └─ KycDocumentationPage.completeKycAndDocumentation()
                     └─ DealClosedWonPage.moveDealToClosedWon()
                          └─ DealProjectCreatedPage.moveDealToProjectCreated()
                               └─ ProjectMilestonePage / ProjectPICreation / ProjectPOCreation
                                    └─ PMTCreditNote / PMTDebitNote
                                         └─ EscrowTransferUptoAccount
                                              └─ EscrowTransferUptoProject
                                                   └─ EscrowTransferUptoSubProject
```

---

### PMT vs. Project Page Objects

There are two parallel sets of page objects for milestone/PI/PO/note flows:

Prefix

Module Entry Point

Used By

`Project*` (no prefix)

Navigated from deal → project

`DealTest`

`PMT*`

Navigated from PMT module directly

`PMTProjectTest`

`PMTCreditNote` and `PMTDebitNote` are shared — both `DealTest` and `PMTProjectTest` use them.

---

### Key Selector Patterns (MUI-specific)

UI Element

Selector

Autocomplete dropdown options

`ul[role='listbox'] li[role='option']`

MUI Dialog

`div.MuiDialog-container, div[role='dialog']`

List item checkbox wrapper

`div.MuiListItemButton-root`

Combobox input

`input[role='combobox']`

MUI Autocomplete popup trigger

`button` inside `.MuiAutocomplete-endAdornment`

---

### Allure Reporting

Results land in `target/allure-results/` (Maven Surefire default). The `allure-maven` plugin reads from there and writes an HTML report to `target/site/allure-maven-plugin/`.

```bash
mvn allure:report   # generate static HTML
mvn allure:serve    # generate + open in browser
```