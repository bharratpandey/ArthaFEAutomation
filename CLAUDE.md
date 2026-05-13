# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Install Playwright browsers (required once after fresh clone)
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

# Compile test sources only (no browser launch)
mvn test-compile

# Generate Allure report (after tests have run)
mvn allure:report

# Open Allure report in browser
mvn allure:serve
```

All tests run against live environments — there is no local mock server. Browsers always launch in headed mode (`setHeadless(false)`).

## Architecture

**Stack**: Java 17 · Maven · Playwright 1.49 · JUnit 5 · Allure

**Pattern**: Page Object Model. All UI interaction is encapsulated in `src/test/java/com/artha/pages/`. Test classes in `src/test/java/com/artha/tests/` call page methods and assert results.

### BaseTest vs. standalone tests

`BaseTest` owns the shared browser lifecycle: one `Playwright` + `Browser` instance per test class (`@BeforeAll`), and a fresh `BrowserContext` + `Page` per test method (`@BeforeEach`/`@AfterEach`). Tests that extend it (`DealTest`, `LoginTest`) get this for free.

`AccountTest` and `PMTProjectTest` do **not** extend `BaseTest` — they manage their own `Playwright`/`Browser` inside `@BeforeAll`/`@AfterAll` (or `@BeforeEach`/`@AfterEach`). `PMTProjectTest` additionally sets `setSlowMo(500)` to pace interactions.

### Target applications

| App | URL | Used by |
|-----|-----|---------|
| KAM (deals/projects/finance) | `https://dev-kam-v2.emb.global` | DealTest, LoginTest, PMTProjectTest, AccountTest |
| Supply (vendor shortlisting) | `https://dev-supply.emb.global` | DealTest (second tab via `page.context().newPage()`) |

### Multi-tab flow

`DealTest.createDealHappyPath` opens a second page in the same `BrowserContext` for the supply vendor flow, then calls `page.bringToFront()` to return to the KAM tab. The supply page object receives its own `Page` reference.

### Credentials

Test credentials are hardcoded as private constants directly in each test class. There is no external config or properties file.

### Debug artifacts

On failure, page classes write screenshots and HTML to `target/debug/<label>/`. `PMTProjectTest` additionally saves a final full-page screenshot to `screenshots/`. Both directories are untracked.

### File uploads

PDF fixtures live in `src/test/resources/` and are referenced by relative path from the project root (e.g. `src/test/resources/demo-proposal.pdf`). They are copied to `target/test-classes/` by Maven during the build.

### Page object conventions

- Every page class takes a `Page` in its constructor and stores it as `private final Page page`.
- Interaction methods return `boolean` (success/failure) or `String` (captured values like IDs or names) so tests can `assertTrue`/`assertNotNull`.
- Each page class implements a `safeType` helper that falls back to JS `dispatchEvent` when Playwright's typed input is not reflected by the framework's React/MUI bindings.
- Autocomplete dropdowns (`ul[role='listbox'] li[role='option']`) are handled via `selectFirstAutocompleteOption`, which tries exact match → partial match → first option → keyboard fallback.