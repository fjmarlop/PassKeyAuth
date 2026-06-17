# GitHub Actions CI Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Add a GitHub Actions workflow that runs all 85 unit+lint tests on every push and every PR to main, providing fast fail-early feedback before merge.

**Architecture:** Single job `test` on `ubuntu-latest`. Two sequential Gradle steps (core tests + lint rule tests) so each is independently visible in the GitHub UI. Gradle cache via `gradle/actions/setup-gradle@v4`. Test reports uploaded as artifacts on failure for easy triage.

**Tech Stack:** GitHub Actions, Gradle 9.1.0, AGP 9.0.0, JDK 17 Temurin, `actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`, `actions/upload-artifact@v4`

**Platform:** Android (JVM-only tests — no emulator, no instrumented tests, no Firebase emulator)

---

## Scope

| What runs in CI | What does NOT run in CI |
|---|---|
| `:passkeyauth-core:testDebugUnitTest` (73 tests: JVM + Robolectric) | Instrumented tests (need physical device: StrongBox/TEE) |
| `:passkeyauth-lint:test` (12 tests) | BiometricPrompt smoke tests |
| | Firebase emulator suite (adapters mocked with MockK) |
| | `sample` app build (needs `google-services.json`) |

## Key constraints

- `sample/google-services.json` is gitignored → we only run tasks that don't compile the sample module.
  The `google-services` plugin v4.4.2 reads the JSON at task execution time, NOT configuration time, so running `:passkeyauth-core:testDebugUnitTest` without the sample JSON is safe.
- `local.properties` is gitignored (contains Windows SDK path). AGP falls back to `ANDROID_HOME` env var, which is set automatically on GitHub Actions `ubuntu-latest`.
- compileSdk=35 is preinstalled on `ubuntu-latest` (ubuntu-24.04) as of 2025.
- JDK 17 required (AGP 9 minimum).

---

## Task 1 — Create `.github/workflows/ci.yml`

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create the workflows directory and CI file**

```bash
mkdir -p .github/workflows
```

Then create `.github/workflows/ci.yml` with the following content:

```yaml
name: CI

on:
  push:
    branches:
      - "**"
  pull_request:
    branches:
      - main

# Cancel in-progress runs on the same branch when a new push arrives.
# Saves runner minutes on stale pushes.
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  test:
    name: Unit Tests
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      # Caches ~/.gradle/caches and ~/.gradle/wrapper automatically.
      # Also enables Build Scan integration on failure.
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      # 73 tests: JVM unit + Robolectric (SecureStorage + Firebase adapters)
      - name: Unit tests – passkeyauth-core
        run: ./gradlew :passkeyauth-core:testDebugUnitTest

      # 12 tests: L1 MissingFragmentActivity, L2 SkipBiometricNavigation, L3 MissingLifecycleHooks
      - name: Lint rule tests – passkeyauth-lint
        run: ./gradlew :passkeyauth-lint:test

      # Upload HTML test reports as downloadable artifact when tests fail.
      # Lets you triage failures without re-running locally.
      - name: Upload test reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports-${{ github.run_number }}
          path: |
            passkeyauth-core/build/reports/tests/testDebugUnitTest/
            passkeyauth-lint/build/reports/tests/test/
          retention-days: 7
```

- [ ] **Step 2: Verify file is well-formed (local lint)**

```bash
cat .github/workflows/ci.yml
```

Expected: no syntax errors visible. File must start with `name: CI`.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add GitHub Actions workflow (JVM unit tests + lint rules)"
```

---

## Task 2 — Push and verify the workflow runs green

**Files:**
- No new files

- [ ] **Step 1: Push the current branch**

```bash
git push origin test/firebase-adapter-tests
```

Wait ~2 minutes for the workflow to complete.

- [ ] **Step 2: Confirm workflow is visible on GitHub**

Navigate to: `https://github.com/fjmarlop/PassKeyAuth/actions`

Expected: a new workflow run named "CI" appears with status "in progress" or "success".

- [ ] **Step 3: Confirm all steps pass**

Expected: both steps show a green checkmark:
- `Unit tests – passkeyauth-core` → green
- `Lint rule tests – passkeyauth-lint` → green

- [ ] **Step 4 (Optional): Enable branch protection**

In GitHub → Settings → Branches → Add rule for `main`:
- ✅ Require a pull request before merging
- ✅ Require status checks to pass: **Unit Tests**
- ✅ Require branches to be up to date before merging

This makes the CI check mandatory before merge.

---

## Verification checklist

- [ ] Workflow file exists at `.github/workflows/ci.yml`
- [ ] CI triggers on push to any branch
- [ ] CI triggers on PR to main
- [ ] Both Gradle tasks complete in < 5 minutes on ubuntu-latest
- [ ] Test reports are uploaded as artifact when a test fails
- [ ] No `google-services.json` or `local.properties` needed in CI

## Future additions (out of scope for this plan)

| Addition | When to add |
|---|---|
| JaCoCo coverage report upload (Codecov/SonarCloud) | When core coverage target >80% |
| `./gradlew :sample:lint` | When `google-services.json` is handled via secret |
| Matrix: API 26 / API 34 Robolectric | If Robolectric tests fail on specific API levels |
| Instrumented test job (self-hosted runner) | When a physical device is available as GH Actions runner |
