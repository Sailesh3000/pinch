# Expense Tracker (Android) — 100% On-Device

A privacy-first Android expense tracker that **passively captures bank/UPI/payment
notifications** (including SMS-originated bank alerts via the Messages app's
notification), extracts transactions with **on-device AI**, and gives you
categorized spend history, deterministic insights, and natural-language Q&A —
with **no SMS access, no account access, and no data leaving the phone**
(one exception: optional, disabled-by-default crash reporting — see
[Privacy](#privacy--permissions)). The on-device AI model ships **bundled with
the app install** via Play Asset Delivery — no separate download needed on
Play Store installs.

Built to the specs in [`expense-tracker-specs.md`](expense-tracker-specs.md),
[`notification-expense-tracker-plan.md`](notification-expense-tracker-plan.md),
and [`expense-tracker-ui-plan.md`](expense-tracker-ui-plan.md).
Requirement → implementation mapping lives in [`traceability.md`](traceability.md).

---

## Feature highlights

| Area | What you get |
|---|---|
| **Ingestion** | `NotificationListenerService` with a sub-5 ms main-thread pre-filter, package whitelist, and async offload |
| **Dedup** | Dual-source correlation (UPI push + bank SMS for the same payment) inside a 180 s rolling window — merged, never duplicated |
| **Extraction** | 3-tier on-device chain: Gemini Nano (AICore) → MediaPipe Gemma/Qwen SLM → deterministic regex fallback. Zero arithmetic — amounts are *copied*, never computed |
| **Confidence gating** | ≥ 0.75 auto-save · 0.50–0.75 auto-save with best-guess · < 0.50 saved to a review queue |
| **Clarification loop** | Interactive notification with 3 category-suggestion buttons → tap resolves the transaction **and** promotes its text pattern into a Tier-0 template cache so the next one extracts in < 1 ms without waking any model |
| **Auto-decay** | WorkManager worker (every 6 h) auto-resolves pending clarifications after 48 h and prunes stale cache templates after 30 d |
| **Insights** | Category donut, 6-month trend, top merchants, subscription detection, anomaly alerts — all computed by deterministic SQL/Kotlin, no chart libraries |
| **NL Q&A** | "How much did I spend on Amazon this month?" → pattern-matched Text-to-SQL over Room, answered on-device |
| **UI** | Jetpack Compose (Material 3): Onboarding → Home / Insights / Review (badge) / Settings, manual-entry fallback, detail bottom sheet |

**All 5 planned phases are implemented** (see [Phases](#implementation-status)).

---

## How it works: the end-to-end notification pipeline

This is the heart of the app. One notification's journey, from the OS to a
categorized row in Room:

```
                ┌────────────────────────────────────────────────────────────────────────┐
                │  A bank/UPI app (or the SMS app delivering a bank SMS) posts a         │
                │  Notification. The OS hands it to our bound listener.                  │
                └────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
        ① TransactionNotificationListenerService.onNotificationPosted()   [main thread, < 5 ms]
           • reads EXTRA_TITLE / EXTRA_TEXT from the Notification extras
           • PreFilterEngine.isFinancialCandidate(pkg, title, body):
              1. package whitelisted?          (PackageWhitelist — in-memory snapshot
                                                 seeded from the monitored_packages table)
              2. negative blocker present?     (OTP, verification code, promos, … → DROP)
              3. positive trigger present?     (debited, credited, ₹, INR, UPI ref, A/c *…, …)
           • if it passes → NotificationEvent pushed into an unbounded Channel
                                        │
                                        ▼
        ② Channel consumer coroutine  [Dispatchers.Default]
                                        │
                                        ▼
        ③ NotificationProcessor.process(event)
           a. LiteralExtraction.firstAmount(rawText)          — amount copied verbatim
           b. DeduplicationEngine.evaluate(amount, currency, text, ts)
              • |Δt| ≤ 180 s AND same amount AND (same normalized payee OR same currency)
              • DUPLICATE → mark the existing row merged_from_dual_source = 1 → stop
              • FRESH → continue
           c. TemplateCacheEngine.lookup(pkg, text)           — Tier 0
              • structural hash (digits/currency stripped → SHA-256)
              • hit → deterministic regex capture, confidence 0.92, skip all models
           d. ExtractorChain.extract()                        — only on Tier-0 miss
              • Gemini Nano (ML Kit GenAI Prompt API) if AICore reports AVAILABLE
              • else MediaPipe SLM (fine-tuned Qwen2.5-0.5B, bundled via Play
                Asset Delivery; falls back to on-demand download for sideloads)
              • else DeterministicRegexExtractor (always available; literal copy only)
              • not financial? → stop
           e. ConfidenceGating.apply(score)
              • ≥ 0.75 HIGH  → save silently
              • 0.50–0.75 MED → save with best-guess category
              • < 0.50 LOW   → save + needs_clarification = 1
           f. CategoryRepository.resolveCategoryId(name)      — seed names, or "Uncategorized"
           g. TransactionRepository.insertFromExtraction(entity) → Room
                                        │
                     ┌─────────────────┴──────────────────┐
                     │ LOW confidence                     │ HIGH/MEDIUM
                     ▼                                    ▼
        ④ ClarificationNotifier.dispatchClarification()   done
           • queries top-3 categories by per-merchant frequency
           • posts a real interactive notification:
             "Categorize ₹1234 INR" + 3 category action buttons
                                        │ user taps a button
                                        ▼
        ⑤ ClarificationActionReceiver.onReceive()
           • updates the transaction (category, isClarified, tier = MANUAL)
           • writes clarification_history
           • TemplateCacheEngine.promote() → the exact text pattern + regex is cached
             → the next identical notification hits Tier 0 and never wakes a model
           • (after 48 h unanswered, ClarificationAutoDecayWorker auto-resolves it
             as AUTO_RESOLVED_AFTER_TIMEOUT instead)
```

**Key properties**

- **Fast path stays fast.** The main thread only ever does one whitelist set-check
  and two regex scans; all model/DB work is offloaded through the Channel.
- **Zero arithmetic.** Every engine *copies* amount literals out of the text.
  SLMs are prompted to extract, never calculate — no hallucinated math.
- **Graceful degradation.** No AICore? No model downloaded? The regex tier keeps
  the whole pipeline functional; Settings shows which engine is active.
- **Privacy.** No `READ_SMS`/`RECEIVE_SMS` (bank SMS text arrives as the
  *Messages app's notification*, which the listener sees), no account access,
  nothing transmitted.

### Testing the pipeline without a phone

Two JVM paths (no emulator, no device) exercise this pipeline:

1. **`NotificationPipelineE2ETest`** (Robolectric + `@HiltAndroidTest`) boots the
   *real* Hilt graph, the *real* Room database (seeded), and the *real*
   Hilt-injected `TransactionNotificationListenerService`, then plays the OS
   role — builds a real `Notification` + `StatusBarNotification` and calls the
   service's real `onNotificationPosted`. Eight scenarios: high-confidence UPI
   debit saved with category, ambiguous charge → review queue **and** a real
   clarification notification posted, dual-source dedup merge, three drop cases
   (non-financial, OTP, unwhitelisted package), a **simulated tap on the
   clarification notification's action button** that drives the real
   `ClarificationActionReceiver` (row resolved + audit trail + Tier-0 promotion
   + notification dismissed), and a **seeded Tier-0 template** proving the next
   identical notification is extracted at confidence 0.92 without waking any
   model. (The Tier-0 scenario caught a real bug: `lookup()` read
   `match.groups["merchant"]`, which throws when the pattern has no such named
   group — every post-promotion notification would have been silently dropped
   on a device. Now guarded.)
2. **`DebugPipelineReceiver`** (on-device, adb):
   ```bat
   adb shell am broadcast -n com.expensetracker/.ingestion.DebugPipelineReceiver ^
     --es title "HDFC Bank" ^
     --es body "INR 1,250.00 debited from A/c XX4567 at Swiggy on 17-Aug-26. Avl bal: INR 12,345.67"
   ```
   Runs extraction → gating → Room on a real device.

---

## Architecture

### Layers

```
ui/            Jetpack Compose screens + ViewModels (MVVM, StateFlow)
  onboarding/  permission flow with disclosure + deep link
  home/        transaction feed, month summary, search/filter, manual entry
  insights/    charts (pure Compose Canvas), subscription + anomaly cards, NL Q&A bar
  review/      clarification queue with suggestion chips (Top-3 + Other)
  settings/    permission status, whitelist editor, AI engine status + model download
  detail/      transaction bottom sheet (re-categorize, raw text, delete)
  navigation/  4-tab NavigationBar (Review tab carries a pending-count badge)
  components/  TransactionRow, ConfidenceDot, CategoryIcon, CategoryChipPicker

ingestion/     listener service, pre-filter, whitelist, dedup, event channel, processor
extraction/    extractor chain + 3 engines, template cache, confidence gating,
               literal extraction, model downloader, capability detector
clarification/ interactive-notification dispatcher + action BroadcastReceiver
insights/      anomaly detector, narrative generator (guardrailed), text-to-SQL
worker/        WorkManager: clarification decay (6 h periodic), model download (one-time)
data/          repositories (transactions, categories, monitored packages, clarification)
core/          database (Room, entities, DAOs, v1→v2 migration, seed data),
               models, deterministic math engine, formatters
di/            Hilt modules (Database, Ingestion, Clarification, Insights)
```

### Database (Room, `expense_tracker.db`)

| Table | Purpose |
|---|---|
| `transactions` | Extracted transactions (amount, type, merchant, category, confidence score/tier, `needs_clarification`, `dedup_hash`, `merged_from_dual_source`, raw text, source package/type, account reference) |
| `categories` | 12 seeded categories (icon, color, `usage_count` for personalization) |
| `monitored_packages` | Whitelist source of truth (20 seeded financial apps + SMS apps, incl. a `com.android.shell` test entry) |
| `clarification_history` | Audit trail of user resolutions (source: notification action / review screen) |
| `template_cache` | Tier-0 structural templates: hash → extraction regex + default category (unique per package+hash) |

`SeedDatabaseCallback` seeds categories + packages at DB creation so IDs are
stable before the first insert. `MIGRATION_1_2` adds the Phase-2 tables.

### Key design decisions (full log in `traceability.md`)

- **Single `:app` module** with spec-style packages inside.
- **Hilt** for all DI; the Hilt Gradle plugin rewrites app classes so
  `@AndroidEntryPoint` components self-inject.
- **Tier-0 confidence is fixed at 0.92** — authoritative enough to skip the
  SLM, short of 1.0 to leave room for manual override.
- **Dedup is in-memory** (process lifetime) — spec-permitted heuristic.
- **Model**: a fine-tuned Qwen2.5-0.5B (`Sailesh3000/pinch_qwen2.5_0.5b_finetuned`,
  ~490 MB) via MediaPipe tasks-genai, fine-tuned on a synthetic bank/UPI
  notification corpus to fix the base model's amount/merchant hallucinations.
  Shipped **bundled via Play Asset Delivery** (install-time); falls back to a
  HuggingFace/GitHub-Releases-mirrored download for sideloaded/dev installs.
- **Zero external charting** — donut/bar charts are hand-rolled Compose Canvas.
- **AAB ~442 MB with the model bundled** (install-time Play Asset Delivery);
  the debug/sideload APK stays small and downloads the model on demand instead.
- **Database encryption**: SQLCipher with a passphrase generated on first run
  and protected by the Android Keystore — the on-disk DB is never plaintext.

### Phases

| Phase | Scope | State |
|---|---|---|
| 1 | Core pipeline: listener, pre-filter, dedup, Nano+regex extraction, gating, Room, Home/Settings/Onboarding | ✅ |
| 2 | Clarification loop: review UI, interactive notifications, Tier-0 promotion, auto-decay worker, DB v2 | ✅ |
| 3 | Deterministic insights: charts, top merchants, confidence stats, subscription detection | ✅ |
| 4 | MediaPipe SLM fallback engine + model download + interactive notification actions | ✅ |
| 5 | Guardrailed narratives, Text-to-SQL Q&A, anomaly detection, JVM screenshot + E2E pipeline tests | ✅ |
| 6 | **Production readiness**: SQLCipher DB encryption, Sentry crash reporting (disabled by default), Play Asset Delivery model bundling, CSV export, expanded bank whitelist (DB v3), ProGuard/R8 release hardening, GitHub Actions CI/CD, privacy policy + ToS + notification-access justification | ✅ |

---

## Privacy & permissions

| Permission | Status |
|---|---|
| `READ_SMS` / `RECEIVE_SMS` | **None** — SMS text is only seen as the Messages app's notification |
| Notification listener | Required; prominent disclosure on the onboarding screen with a settings deep link. See `docs/NOTIFICATION_ACCESS_JUSTIFICATION.md` |
| `INTERNET` | Model comes bundled via Play Asset Delivery on Play Store installs (no network use); sideloads use it for a one-time fallback model download. Also carries opt-in crash diagnostics if Sentry is ever enabled |
| Storage | Encrypted SQLCipher database (Android Keystore-managed passphrase); model file in the PAD asset pack or internal storage |

**Data at rest:**
- Database is SQLCipher-encrypted; passphrase is generated on first launch and never leaves the Keystore.
- Android Auto Backup is enabled for the encrypted DB only (device-to-device transfer); the AI model file is always excluded from backup.
- **Crash reporting (Sentry)** is disabled by default (no DSN shipped). If ever enabled, a `beforeSend` scrubber strips any amount/merchant/UPI/bank-looking data before an event leaves the device — see `docs/PRIVACY_POLICY.md`.
- **Data export**: Settings → Data Management → Export to CSV, via the system file picker.

Full policy: `docs/PRIVACY_POLICY.md` · Terms: `docs/TERMS_OF_SERVICE.md` ·
hosted at https://sailesh3000.github.io/pinch/.

---

## Testing — 20 unit test classes + 3 instrumentation tests, mostly no device needed

| Test class | What it covers |
|---|---|
| `PreFilterEngineTest` | Positive/negative keyword gating |
| `DeduplicationEngineTest` | 180 s window, amount/payee/currency matching, window expiry |
| `NotificationPipelineE2ETest` | **Full pipeline E2E** (Robolectric + real Hilt + real Room): high-confidence save, low-confidence → review + real clarification notification, dual-source dedup merge, non-financial / OTP / unwhitelisted drops, clarification-action tap → real `ClarificationActionReceiver` (resolve + history + Tier-0 promotion + dismiss), seeded Tier-0 template → model-free 0.92 extraction |
| `E2EUiTest` | End-to-end UI flow coverage |
| `TemplateCacheEngineTest` | Structural hash, regex generation, Tier-0 lookup |
| `ExtractorChainTest` | Nano → MediaPipe → Regex routing, fallbacks, hot-swap |
| `EngineTypeTest` | Engine enum + capability routing |
| `MediaPipeExtractorTest` | Engine availability (no native init on JVM) |
| `ModelDownloaderTest` | Download state machine, URL constants |
| `ModelDownloaderResumeTest` | HTTP `Range`-header resume from a partial `.tmp` file and fallback-URL failover, against a real loopback HTTP server (Robolectric) |
| `DeterministicAggregationTest` | Exact month-summary math |
| `SubscriptionDetectorTest` | Monthly/annual/bi/quarterly detection, tolerances |
| `AnomalyDetectorTest` | Large-spend + unfamiliar-merchant flags |
| `NarrativeGeneratorTest` | Guardrailed narrative fallback + claims |
| `ZeroArithmeticPromptGuardTest` | Ensures extraction prompts never ask a model to compute, only copy |
| `TextToSQLTranslatorTest` | NL patterns → query plans |
| `ClarificationAutoDecayTest` | 48 h auto-resolve worker logic |
| `BenchmarkSuiteTest` / `DatasetExportTest` | 1000+-sample synthetic bank/UPI notification corpus, per-engine accuracy benchmarking, dataset export for fine-tuning |
| `ScreenshotTest` | Robolectric + Roborazzi: onboarding, home, review, insights (± query), settings — real ViewModels over fake DAOs |

**Instrumentation tests** (`app/src/androidTest/`, real device/emulator):
`DeterministicRegexExtractorInstrumentedTest`, `ExtractorChainInstrumentedTest`,
`NotificationListenerServiceTest`. Manual device-matrix validation plan in
`docs/instrumentation_test_plan.md`.

### Running tests (Windows)

```bat
:: full suite (all 100)
.\gradlew.bat :app:testDebugUnitTest

:: just the E2E pipeline test
.\gradlew.bat :app:testDebugUnitTest --tests "com.expensetracker.ingestion.NotificationPipelineE2ETest"

:: regenerate screenshot references (after intentional UI changes)
.\gradlew.bat :app:recordRoborazziDebug --tests "com.expensetracker.ui.ScreenshotTest"

:: pixel-compare regression gate (CI-friendly; fails on visual drift)
.\gradlew.bat :app:verifyRoborazziDebug
```

Screenshot references live in `app/build/roborazzi/` (1080×2220 xxhdpi).

### Build

```bat
.\gradlew.bat :app:assembleDebug     :: debug APK
.\gradlew.bat :app:bundleRelease     :: signed release AAB for Play Store (needs keystore.properties)
.\gradlew.bat :app:assembleRelease   :: signed release APK, R8/ProGuard applied
```

Release signing reads `keystore.properties` (gitignored, never committed) at
the repo root:

```properties
storeFile=<path-to-keystore.jks>
storePassword=<...>
keyAlias=<...>
keyPassword=<...>
```

The on-device model (`aimodel/src/main/assets/qwen-pinch.litertlm`, ~490 MB)
is **not committed** — download it before a release build:

```bat
curl -L -o aimodel\src\main\assets\qwen-pinch.litertlm ^
  https://huggingface.co/Sailesh3000/pinch_qwen2.5_0.5b_finetuned/resolve/main/qwen-pinch.litertlm
```

---

## Project layout

```
expense_tracker/
├── app/src/main/java/com/expensetracker/
│   ├── ExpenseTrackerApp.kt          @HiltAndroidApp, Sentry init, WorkManager Configuration.Provider
│   ├── MainActivity.kt               Compose entry (onboarding gate → MainScaffold)
│   ├── ingestion/                    listener service, pre-filter, whitelist, dedup, processor
│   ├── extraction/                   extractor chain, 3 engines, template cache, gating,
│   │                                 ModelDownloader (resume + mirrors), ModelAssetProvider (PAD)
│   ├── clarification/                ClarificationNotifier + ClarificationActionReceiver
│   ├── insights/                     TextToSQLTranslator, AnomalyDetector, NarrativeGenerator
│   ├── worker/                       ClarificationAutoDecayWorker, ModelDownloadWorker, WorkScheduler
│   ├── data/                         repositories (incl. CSV export) + AppPreferences
│   ├── core/                         Room (entities/DAOs/migrations v1-v3, seed),
│   │                                 DatabaseKeyProvider + LegacyDatabaseMigrator (SQLCipher),
│   │                                 models, math, formatters
│   ├── di/                           Hilt modules
│   └── ui/                           Compose screens, components, theme, navigation
├── aimodel/                          Play Asset Delivery module (install-time)
│   └── src/main/assets/              qwen-pinch.litertlm goes here (not committed, see Build)
├── app/src/test/java/com/expensetracker/     20 unit test classes (see Testing)
├── app/src/androidTest/java/com/expensetracker/   3 instrumentation tests (real device)
├── .github/workflows/                ci.yml (test/lint/build), deploy-privacy-policy.yml (Pages)
├── docs/                             PRIVACY_POLICY.md, TERMS_OF_SERVICE.md,
│                                     NOTIFICATION_ACCESS_JUSTIFICATION.md,
│                                     instrumentation_test_plan.md, PLAY_STORE_LISTING.md, index.html
├── expense-tracker-specs.md          product/technical spec (FR-IDs, schemas, NFRs)
├── notification-expense-tracker-plan.md   pipeline design
├── expense-tracker-ui-plan.md        UI plan
└── traceability.md                   spec → code traceability + decision log
```

## CI/CD & release status

- **GitHub Actions** (`.github/workflows/ci.yml`): unit tests + lint + debug APK
  build on every push/PR; a signed-release/R8 verification job runs on
  `release/*` branches.
- **Legal docs** (`.github/workflows/deploy-privacy-policy.yml`): deploys
  `docs/` to GitHub Pages at https://sailesh3000.github.io/pinch/ on every
  push touching `docs/**`.
- **Play Store**: production-hardened for closed-beta submission — SQLCipher
  encryption, ProGuard/R8-verified release AAB, Play Asset Delivery model
  bundling, CSV export/backup, privacy policy + ToS + notification-access
  justification all in place.

## Environment

- Windows, git-bash or `cmd`; JDK 23 (`C:/Program Files/Java/jdk-23`)
- Android SDK at `C:/Users/Sailesh/AppData/Local/Android/Sdk` (platform 35, build-tools 35.0.0)
- Gradle wrapper 8.14.3, AGP 8.9.2, Kotlin 2.1.20, KSP (Hilt + Room), compileSdk 35, minSdk 26

## License

Private project — no license.
