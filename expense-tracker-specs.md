# AI Expense Tracker (Android) — Comprehensive Product & Technical Specifications

**Version:** 2.1.0 (100% On-Device Architecture & Strict Zero-Arithmetic SLM Rule)  
**Target Platform:** Android (API Level 26+ / Android 8.0 Oreo and above; recommended Android 14+ with AICore / NPU hardware acceleration)  
**Reference Plans:** [notification-expense-tracker-plan.md](file:///c:/My_Agentic_Projects/expense_tracker/notification-expense-tracker-plan.md) & [expense-tracker-ui-plan.md](file:///c:/My_Agentic_Projects/expense_tracker/expense-tracker-ui-plan.md)  
**Core Architectural Mandates:**
1. **100% On-Device SLM Only:** Zero cloud server dependencies, zero external network API calls for transaction parsing, zero financial data leaves the user's device.
2. **Zero-Arithmetic SLM Rule:** The on-device SLM is **never** asked or permitted to perform mathematical calculations, additions, subtractions, averages, or percentage derivations. All numbers, sums, deltas, and trends are 100% pre-computed by the Kotlin/SQLite Deterministic Math Engine. The SLM is strictly an *extractor* of text tokens and a *linguistic narrator* over pre-computed facts.  
**Document Status:** Approved Baseline Specification  

---

## Table of Contents
1. [Executive Summary & 100% On-Device Vision](#1-executive-summary--100-on-device-vision)
2. [Architectural Overview & Data Flow](#2-architectural-overview--data-flow)
3. [Zero-Arithmetic Architecture & Deterministic Math Engine](#3-zero-arithmetic-architecture--deterministic-math-engine)
4. [Functional Requirements (FR)](#4-functional-requirements-fr)
5. [Data Models & Schema Specifications](#5-data-models--schema-specifications)
6. [On-Device AI & Extraction Pipeline](#6-on-device-ai--extraction-pipeline)
7. [UI/UX & Component Specifications](#7-uiux--component-specifications)
8. [Insights, Analytics & Natural Language Query](#8-insights-analytics--natural-language-query)
9. [Security, Privacy & Play Store Compliance](#9-security-privacy--play-store-compliance)
10. [Non-Functional Requirements (NFR) & SLAs](#10-non-functional-requirements-nfr--slas)
11. [Phased Implementation Roadmap & Milestones](#11-phased-implementation-roadmap--milestones)
12. [Verification & Testing Strategy](#12-verification--testing-strategy)

---

## 1. Executive Summary & 100% On-Device Vision

### 1.1 Problem Statement
Traditional mobile expense trackers suffer from four critical flaws:
- **Manual Data Entry:** High cognitive load and friction lead to user churn within 2–3 weeks.
- **Fragile SMS Regex Parsers:** Hardcoded regex rules break whenever banks tweak notification wording, and fail completely for modern app notifications (Google Pay, PhonePe, Paytm, CRED).
- **Privacy Intrusion & Cloud Risks:** Sending sensitive bank notifications, transaction amounts, account numbers, and payee names to cloud LLMs creates privacy liabilities and ongoing server costs.
- **LLM Hallucinations in Arithmetic:** Small Language Models (and LLMs generally) are notoriously poor at exact arithmetic, summation, and calculating percentages. Asking an AI model to add up transactions or compute spend trends inevitably produces hallucinated totals, corrupting user financial balances.

### 1.2 The Solution: 100% On-Device + Zero-Arithmetic Separation
The **AI Expense Tracker** pairs local on-device SLMs with a deterministic database engine:
1. **Universal Notification Ingestion:** Uses Android's `NotificationListenerService` to capture app notifications (UPI, banking apps, digital wallets) and SMS notifications via default messaging apps without requiring restricted `READ_SMS` permissions.
2. **Two-Stage Local Extraction Pipeline:**
   - **Tier 0 (Template Cache & Regex Signatures):** Instant, zero-computation local regex matching for previously seen notification formats.
   - **Tier 1 (On-Device Small Language Model - SLM):** Gemini Nano via Android ML Kit GenAI Prompt API on supported devices, with a lightweight local quantized SLM (Gemma 3 1B/2B / Qwen2.5 1.5B via MediaPipe LLM Inference) on other hardware.
3. **Strict Zero-Arithmetic SLM Rule:**
   - In **Extraction**: The SLM only extracts literal text values (`amount: 850.00`) directly present in the notification. It performs zero mathematical operations.
   - In **Insights & Trends**: 100% of arithmetic (totals, monthly deltas, percentages, category splits, subscription renewal dates) is computed deterministically in Kotlin/SQLite. The SLM receives only pre-calculated facts and acts solely as a linguistic narrator.
   - In **Natural Language Query**: The SLM generates parameter-bound SQL queries (`SELECT SUM(amount)...`). The SQLite engine executes the math and returns the exact number.
4. **Human-in-the-Loop Clarification (No Cloud Fallback):** When the on-device model is uncertain (confidence $< 0.75$), the system does not guess or call a server. Instead, it presents an intuitive one-tap chip prompt to the user. User selections instantly compile into the local Tier 0 cache for future zero-shot hits.

### 1.3 Core Design Principles
- **Principle 1 — Absolute Device Privacy:** Financial notification payloads, balances, and spend history never leave the device. No server backend, no third-party cloud LLM API, and no telemetry on transaction content.
- **Principle 2 — Absolute Numerical Determinism:** No number displayed in the application is ever computed, estimated, or derived by an SLM. All arithmetic originates from verified SQLite aggregates.
- **Principle 3 — Visible Confidence:** Every transaction visualizes categorization confidence (`●` High, `◐` Medium/Best Guess, `○` Low/Pending Review).
- **Principle 4 — Never Block on AI:** Notifications are ingested and recorded locally immediately; extraction and clarification happen asynchronously.
- **Principle 5 — Ask Like a Human, Not a Form:** Clarification prompts are one-tap chips with free-text fallback.

---

## 2. Architectural Overview & Data Flow

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                               Android OS Notification Subsystem                        │
│          ┌───────────────────────────┐         ┌───────────────────────────┐           │
│          │ Bank / UPI / Wallet Apps  │         │   Default SMS / Messages  │           │
│          └─────────────┬─────────────┘         └─────────────┬─────────────┘           │
└────────────────────────┼─────────────────────────────────────┼─────────────────────────┘
                         │                                     │
                         └──────────────────┬──────────────────┘
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        Ingestion Layer (Unified Notification Pipeline)                  │
│                                                                                        │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │ TransactionNotificationListenerService (:NotificationListenerService)          │   │
│   │ Extracts: packageName, title, bodyText, timestamp, notificationKey, subText     │   │
│   └───────────────────────────────────────┬────────────────────────────────────────┘   │
│                                           ▼                                            │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │ PreFilterEngine (Instant in-memory fast-path)                                  │   │
│   │ 1. Package Whitelist verification (PhonePe, GPay, Paytm, Banks, Messages)      │   │
│   │ 2. Financial Keyword Regex match ("debited", "spent", "credited", "INR", "₹")  │   │
│   │ 3. Drop non-financial noise immediately (OTPs, promos, social alerts)          │   │
│   └───────────────────────────────────────┬────────────────────────────────────────┘   │
│                                           ▼                                            │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │ DeduplicationEngine                                                            │   │
│   │ Merges dual notifications (e.g., Bank app push + SMS alert for same txn)       │   │
│   │ Heuristic: hash(amount, payee_norm, timestamp_window ±180s)                    │   │
│   └───────────────────────────────────────┬────────────────────────────────────────┘   │
└───────────────────────────────────────────┼────────────────────────────────────────────┘
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                      100% On-Device Extraction & Routing Engine                        │
│                                                                                        │
│                  ┌─────────────────────────────────────────────────┐                   │
│                  │  Tier 0: Template Cache & Regex Signature Match │                   │
│                  │  (Instant local regex matching — 0% AI compute) │                   │
│                  └────────┬───────────────────────────────┬────────┘                   │
│                    Hit    │                               │ Miss / Low Conf            │
│              (Conf ≥ 0.90)│                               ▼                            │
│                           │        ┌──────────────────────────────────────────────┐    │
│                           │        │ Tier 1: On-Device SLM (Literal Extractor)    │    │
│                           │        │ ├─ Primary: Gemini Nano (ML Kit Prompt API)  │    │
│                           │        │ └─ Fallback: MediaPipe Local SLM             │    │
│                           │        │    (Gemma 3 1B/2B / Qwen2.5 1.5B 4-bit)      │    │
│                           │        │ (Copies exact amount/merchant — NO ARITHMETIC│    │
│                           │        └──────────────────────┬───────────────────────┘    │
│                           │                               │                            │
│                           └───────────────────────┬───────┘                            │
│                                                   ▼                                    │
│                                   ┌──────────────────────────────┐                     │
│                                   │ Structured Transaction Event │                     │
│                                   │ {amount, merchant, category, │                     │
│                                   │  type, date, confidence}     │                     │
│                                   └──────────────┬───────────────┘                     │
└──────────────────────────────────────────────────┼─────────────────────────────────────┘
                                                   │
                         ┌─────────────────────────┴─────────────────────────┐
                         │                                                   │
          Confidence ≥ 0.75                                   Confidence < 0.75
                         ▼                                                   ▼
┌────────────────────────────────────────────────┐  ┌────────────────────────────────────┐
│ Auto-Save Pipeline                             │  │ Clarification Queue (Human in Loop)│
│ - Direct write to Room SQLite DB               │  │ - Add to pending review list/badge │
│ - Status: AUTO_CATEGORIZED                     │  │ - Trigger one-tap notification chip│
│ - Silently update balances & insights          │  │ - On user answer: update DB &      │
│                                                │  │   promote signature to Tier 0 Cache│
└────────────────────────┬───────────────────────┘  └─────────────────┬──────────────────┘
                         │                                            │
                         └──────────────────────┬─────────────────────┘
                                                ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                         Local Storage & Presentation Layer                             │
│                                                                                        │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │ Room Database (SQLite — Local Offline Source of Truth)                         │   │
│   │ Tables: transactions, categories, clarification_history, template_cache,        │   │
│   │         subscriptions, monitored_packages                                      │   │
│   └───────────────────────────────────────┬────────────────────────────────────────┘   │
│                                           │                                            │
│               ┌───────────────────────────┴───────────────────────────┐                │
│               ▼                                                       ▼                │
│ ┌──────────────────────────────────────────┐    ┌──────────────────────────────────┐   │
│ │ Deterministic Math Engine (Kotlin/SQL)   │    │ On-Device SLM Narrator           │   │
│ │ - Computes exact totals, sums, averages  │───>│ - Receives pre-calculated facts  │   │
│ │ - Computes exact month-over-month deltas │    │ - Generates linguistic summary   │   │
│ │ - 100% verified arithmetic (0% AI math)  │    │ - FORBIDDEN from calculating math│   │
│ └─────────────────────┬────────────────────┘    └─────────────────┬────────────────┘   │
│                       │                                           │                    │
│                       └─────────────────────┬─────────────────────┘                    │
│                                             ▼                                          │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │ Presentation Layer (Jetpack Compose)                                           │   │
│   │ ├── Home Screen (Feed, daily group, verified balance header)                   │   │
│   │ ├── Review Screen (Clarification cards, chip selectors)                        │   │
│   │ ├── Insights Screen (Deterministic charts + Guardrailed SLM Narrative Cards)   │   │
│   │ └── Settings (Permissions, on-device AI status, cache auditor, whitelist)      │   │
│   └────────────────────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Zero-Arithmetic Architecture & Deterministic Math Engine

### 3.1 Why SLMs Must Never Do Arithmetic
Small Language Models are probabilistic token predictors, not calculators. Prompting an SLM with raw transaction lists and asking *"What was my total spend on food?"* or *"Calculate my percentage increase vs last month"* frequently results in:
1. Off-by-one errors and missed transactions in long context windows.
2. Inaccurate floating-point arithmetic (e.g. `₹850.50 + ₹1,240.25 = ₹2,091.00`).
3. Hallucinated percentage deltas (e.g. stating spend is "up 45%" when verified SQL delta is "+18.2%").

### 3.2 The Deterministic Pipeline Contract
To guarantee mathematical perfection, the system establishes a strict separation of concerns:

```
┌────────────────────────────────────────────────────────────────────────┐
│                 Step 1: Deterministic Math in Room SQLite               │
│                                                                        │
│  val curFoodSpend = dao.getCategorySpend(FOOD_ID, curMonthStart)       │ // 14200.0
│  val prevFoodSpend = dao.getCategorySpend(FOOD_ID, prevMonthStart)     │ // 10750.0
│  val deltaAmount = curFoodSpend - prevFoodSpend                        │ // +3450.0
│  val deltaPercentage = ((deltaAmount / prevFoodSpend) * 100.0)         │ // +32.09%
│  val topMerchant = dao.getTopMerchant(FOOD_ID, curMonthStart)          │ // "Swiggy", 8 txns
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Pre-calculated String Fact Tokens
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│             Step 2: Guardrailed Fact Payload Injection                 │
│                                                                        │
│  val factsPayload = SpendFacts(                                        │
│      categoryName = "Food & Dining",                                   │
│      currentSpendFormatted = "₹14,200",                                │
│      deltaFormatted = "+₹3,450 (+32.1%)",                              │
│      topMerchantName = "Swiggy",                                       │
│      topMerchantTxnCount = 8,                                          │
│      topMerchantSpendFormatted = "₹4,850"                              │
│  )                                                                     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│             Step 3: On-Device SLM Linguistic Narration Only            │
│                                                                        │
│  "You spent ₹14,200 on Food & Dining this month (+32.1% vs last       │
│   month), largely driven by 8 orders on Swiggy totaling ₹4,850."       │
│                                                                        │
│  [SLM Rule: ONLY use provided numbers. ZERO calculation permitted.]    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Functional Requirements (FR)

### Module 1: Ingestion & Pre-Filtering Engine (`FR-INGEST`)
- **FR-INGEST-01: System Notification Listener:** Implement `TransactionNotificationListenerService` extending Android's `NotificationListenerService`. Listen for `onNotificationPosted` events across all active packages.
- **FR-INGEST-02: Package Whitelist Enforcement:** Maintain an internal database of whitelisted financial applications:
  - Default SMS Apps: `com.google.android.apps.messaging`, `com.samsung.android.messaging`, `com.android.mms`.
  - Indian Banking & UPI: PhonePe (`com.phonepe.app`), Google Pay (`com.google.android.apps.nbu.paisa.user`), Paytm (`net.one97.paytm`), CRED (`com.dreamplug.androidapp`), HDFC MobileBanking, ICICI iMobile, SBI YONO, Axis Mobile, Kotak 811, BHIM.
  - Global/Cards: Standard Chartered, Chase, Citi, Revolut, Monzo, Amex.
  - Custom Package Support: Allow users to add arbitrary package names to the whitelist via Settings.
- **FR-INGEST-03: Keyword Fast-Path Pre-Filter:** Execute an in-memory regex pre-filter before any disk or model invocation:
  - Positive triggers (case-insensitive): `debited`, `credited`, `spent`, `transferred`, `paid`, `sent to`, `received from`, `UPI Ref`, `A/c *`, `VPA`, `INR`, `Rs`, `₹`, `USD`, `$`, `EUR`, `€`.
  - Negative blockers (drop immediately in-memory): `OTP`, `verification code`, `security code`, `login alert`, `pre-approved loan`, `exclusive offer`, `discount coupon`.
- **FR-INGEST-04: Non-Blocking Execution:** `onNotificationPosted` must complete execution in under 5ms on the main thread, dispatching candidate notifications to an asynchronous Kotlin Flow queue.

### Module 2: Deduplication Engine (`FR-DEDUP`)
- **FR-DEDUP-01: Dual-Source Notification Correlation:** Correlate notifications arriving from multiple sources for a single economic transaction (e.g. Bank SMS + UPI App push notification).
- **FR-DEDUP-02: Dedup Algorithm:**
  - Rolling Lookback Window: $T_{\text{dedup}} = 180\text{ seconds}$ (3 minutes).
  - Match Criteria: Matching occurs if `abs(timestamp_A - timestamp_B) <= 180s` AND `amount_A == amount_B` AND (`normalized_payee_A == normalized_payee_B` OR `currency_A == currency_B`).
  - Merge Strategy: Retain the richer notification body; set `merged_from_dual_source = true` and archive both raw notification strings.

### Module 3: 100% On-Device Extraction & AI Parsing (`FR-EXTRACT`)
- **FR-EXTRACT-01: Tier 0 Template Cache Matching (Instant Local Regex):**
  - Compute a structural token hash from the incoming notification text (replacing numbers, dates, and amounts with placeholders).
  - If a matching regex signature exists in `template_cache` with confidence $\ge 0.90$, execute regex extraction instantly without waking up the SLM.
- **FR-EXTRACT-02: Tier 1 On-Device SLM (Literal Extractor — No Math):**
  - **Primary Engine:** Gemini Nano via Android ML Kit GenAI Prompt API for devices supporting Android AICore (Pixel 8+, Galaxy S24+, OnePlus 12+, Xiaomi 14+, etc.).
  - **Local Engine Fallback:** For non-Nano devices, execute a local 4-bit quantized SLM (Gemma 3 1B/2B or Qwen2.5 1.5B) bundled via MediaPipe LLM Inference API or `llama.cpp` Android JNI.
  - The model strictly copies the literal numerical amount and merchant name. It performs zero mathematical operations, splits, or currency conversions.
- **FR-EXTRACT-03: Zero Cloud Transmission Guarantee:** The extraction pipeline contains no network dependencies. If on-device extraction is uncertain, it routes directly to the human-in-the-loop clarification queue.
- **FR-EXTRACT-04: Confidence Gating:**
  - $\text{Confidence} \ge 0.75$: Flag as `CONFIDENCE_HIGH` $\rightarrow$ Auto-save silently to DB.
  - $0.50 \le \text{Confidence} < 0.75$: Flag as `CONFIDENCE_MEDIUM` $\rightarrow$ Auto-save with best-guess category, mark `needs_clarification = false`, tag with `◐` indicator.
  - $\text{Confidence} < 0.50$: Flag as `CONFIDENCE_LOW` $\rightarrow$ Auto-save with `category = "Uncategorized"`, mark `needs_clarification = true`, tag with `○` indicator, queue in Review.

### Module 4: Human-in-the-Loop Clarification (`FR-CLARIFY`)
- **FR-CLARIFY-01: Clarification Queue:** Manage transactions flagged with `needs_clarification = true` in a dedicated reactive list.
- **FR-CLARIFY-02: One-Tap Category Chips:** Display top 3 user-personalized category suggestions based on past category frequency for the merchant/keyword + an "Other" chip.
- **FR-CLARIFY-03: Interactive Android Notification Actions:** For high-priority ambiguous debits, dispatch an interactive Android notification containing 3 inline action buttons (e.g., `[Food]`, `[Groceries]`, `[Transport]`). Answering directly resolves the transaction without opening the app.
- **FR-CLARIFY-04: Self-Learning Feedback Loop (Tier 0 Cache Promotion):** Upon user category selection:
  1. Update `transactions.category` in Room DB.
  2. Insert record into `clarification_history`.
  3. Compile new template signature into `template_cache` (Tier 0 promotion) so subsequent notifications from that merchant/bank parse with 0% SLM compute.
  4. Decrement Review queue badge count.
- **FR-CLARIFY-05: Queue Aging & Auto-Decay:** If a transaction remains unclarified after 48 hours:
  - Transition state from `CONFIDENCE_LOW` to `AUTO_RESOLVED_AFTER_TIMEOUT`.
  - Fall back to the model's best guess or "Uncategorized".
  - Decrement badge count to prevent notification fatigue.

### Module 5: Deterministic Aggregations & On-Device Insights (`FR-INSIGHT`)
- **FR-INSIGHT-01: Deterministic Math Aggregations:** Calculate all month-to-date spending, category distributions, daily averages, and month-over-month % deltas using pure SQLite queries. No LLM arithmetic is permitted.
- **FR-INSIGHT-02: Guardrailed SLM Spending Narrator (`InsightCard`):**
  - Pass pre-calculated, formatted string tokens (e.g. `{{CURRENT_SPEND}} = "₹14,200"`, `{{DELTA_PCT}} = "+32.1%"`, `{{ORDER_COUNT}} = 8`) to the local SLM.
  - The SLM generates a concise 2-sentence conversational narrative using the exact tokens provided.
  - Provide a one-tap deep link from every narrative claim to the backing filtered transaction list.
- **FR-INSIGHT-03: Subscription & Recurring Spend Detector:** Heuristic detector identifying transactions with matching merchant and amounts recurring every $30 \pm 3$ days or $365 \pm 5$ days.
- **FR-INSIGHT-04: On-Device Natural Language Spend Q&A (Text-to-SQL):**
  - Conversational query bar (e.g. *"How much did I spend on Swiggy last month?"*).
  - The local SLM translates the user's question into a parameterized SQL aggregate query (e.g. `SELECT SUM(amount) FROM transactions WHERE clean_payee = 'Swiggy' AND timestamp BETWEEN :start AND :end`).
  - SQLite executes the query deterministically and returns the exact number. The SLM does not perform math on individual rows.

---

## 5. Data Models & Schema Specifications

### 5.1 Room SQLite Database Entities

#### Entity 1: `TransactionEntity` (`table_name = "transactions"`)
```kotlin
package com.expensetracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["category_id"]),
        Index(value = ["merchant_name"]),
        Index(value = ["needs_clarification"]),
        Index(value = ["dedup_hash"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "currency")
    val currency: String = "INR",

    @ColumnInfo(name = "txn_type")
    val txnType: String, // "DEBIT", "CREDIT", "TRANSFER"

    @ColumnInfo(name = "merchant_name")
    val merchantName: String,

    @ColumnInfo(name = "clean_payee")
    val cleanPayee: String?,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long, // Epoch milliseconds

    @ColumnInfo(name = "source_package")
    val sourcePackage: String,

    @ColumnInfo(name = "source_type")
    val sourceType: String, // "APP_NOTIFICATION", "SMS_NOTIFICATION", "MANUAL_ENTRY"

    @ColumnInfo(name = "raw_notification_text")
    val rawNotificationText: String,

    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float, // 0.00 to 1.00

    @ColumnInfo(name = "confidence_tier")
    val confidenceTier: String, // "TIER_0_CACHE", "TIER_1_LOCAL_SLM", "MANUAL"

    @ColumnInfo(name = "needs_clarification")
    val needsClarification: Boolean = false,

    @ColumnInfo(name = "is_clarified")
    val isClarified: Boolean = false,

    @ColumnInfo(name = "dedup_hash")
    val dedupHash: String?,

    @ColumnInfo(name = "merged_from_dual_source")
    val mergedFromDualSource: Boolean = false,

    @ColumnInfo(name = "account_reference")
    val accountReference: String?, // e.g., "A/c *1234"

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
```

#### Entity 2: `CategoryEntity` (`table_name = "categories"`)
```kotlin
package com.expensetracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String, // "Food & Dining", "Groceries", "Rent", "Transportation", "Shopping", "Bills & Utilities", "Entertainment", "Health", "Investment", "Transfer", "Other"

    @ColumnInfo(name = "icon_key")
    val iconKey: String,

    @ColumnInfo(name = "color_hex")
    val colorHex: String,

    @ColumnInfo(name = "is_system_default")
    val isSystemDefault: Boolean = true,

    @ColumnInfo(name = "usage_count")
    val usageCount: Int = 0
)
```

#### Entity 3: `TemplateCacheEntity` (`table_name = "template_cache"`)
```kotlin
package com.expensetracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "template_cache",
    indices = [
        Index(value = ["package_name", "pattern_hash"], unique = true)
    ]
)
data class TemplateCacheEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "pattern_hash")
    val patternHash: String,

    @ColumnInfo(name = "regex_pattern")
    val regexPattern: String, // Compiled extraction regex with named capture groups

    @ColumnInfo(name = "default_category_id")
    val defaultCategoryId: Long,

    @ColumnInfo(name = "merchant_capture_group")
    val merchantCaptureGroup: String = "merchant",

    @ColumnInfo(name = "amount_capture_group")
    val amountCaptureGroup: String = "amount",

    @ColumnInfo(name = "hit_count")
    val hitCount: Long = 0,

    @ColumnInfo(name = "last_hit_timestamp")
    val lastHitTimestamp: Long = System.currentTimeMillis()
)
```

#### Entity 4: `ClarificationHistoryEntity` (`table_name = "clarification_history"`)
```kotlin
package com.expensetracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clarification_history")
data class ClarificationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,

    @ColumnInfo(name = "raw_text")
    val rawText: String,

    @ColumnInfo(name = "suggested_category_id")
    val suggestedCategoryId: Long?,

    @ColumnInfo(name = "chosen_category_id")
    val chosenCategoryId: Long,

    @ColumnInfo(name = "response_time_ms")
    val responseTimeMs: Long,

    @ColumnInfo(name = "clarification_source")
    val clarificationSource: String, // "NOTIFICATION_CHIP", "IN_APP_REVIEW", "DETAIL_VIEW_CORRECTION"

    @ColumnInfo(name = "answered_at")
    val answeredAt: Long = System.currentTimeMillis()
)
```

#### Entity 5: `SubscriptionEntity` (`table_name = "subscriptions"`)
```kotlin
package com.expensetracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "merchant_name")
    val merchantName: String,

    @ColumnInfo(name = "expected_amount")
    val expectedAmount: Double,

    @ColumnInfo(name = "currency")
    val currency: String = "INR",

    @ColumnInfo(name = "billing_cycle")
    val billingCycle: String, // "MONTHLY", "YEARLY", "WEEKLY"

    @ColumnInfo(name = "last_billed_timestamp")
    val lastBilledTimestamp: Long,

    @ColumnInfo(name = "next_renewal_timestamp")
    val nextRenewalTimestamp: Long,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)
```

---

## 6. On-Device AI & Extraction Pipeline

### 6.1 Extraction JSON Schema (Structured Output Protocol)
All Tier 1 (On-Device SLM) invocations enforce the following strict JSON schema:

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "ExpenseExtractionResult",
  "type": "object",
  "properties": {
    "is_financial_transaction": {
      "type": "boolean",
      "description": "True if the notification represents an actual monetary debit, credit, or bill payment."
    },
    "amount": {
      "type": "number",
      "description": "Extracted numerical currency amount directly from the text without alteration (e.g. 850.00)."
    },
    "currency": {
      "type": "string",
      "enum": ["INR", "USD", "EUR", "GBP", "AED", "SGD", "CAD", "AUD", "OTHER"],
      "default": "INR"
    },
    "txn_type": {
      "type": "string",
      "enum": ["DEBIT", "CREDIT", "TRANSFER", "UNKNOWN"],
      "description": "Type of economic movement."
    },
    "merchant_or_payee": {
      "type": "string",
      "description": "Extracted merchant, store, or individual payee name cleaned of noise."
    },
    "account_reference": {
      "type": "string",
      "description": "Masked bank account or card identifier (e.g. 'A/c *1234', 'Card XX9012')."
    },
    "category": {
      "type": "string",
      "enum": [
        "Food & Dining",
        "Groceries",
        "Transportation",
        "Shopping",
        "Bills & Utilities",
        "Rent",
        "Entertainment",
        "Health & Medical",
        "Investment",
        "Friend/Transfer",
        "Salary/Income",
        "Uncategorized"
      ]
    },
    "confidence_score": {
      "type": "number",
      "minimum": 0.0,
      "maximum": 1.0,
      "description": "The model's calibrated confidence in amount, merchant, and category accuracy."
    },
    "reasoning": {
      "type": "string",
      "description": "Brief explanation of how the category and merchant were derived."
    }
  },
  "required": [
    "is_financial_transaction",
    "amount",
    "currency",
    "txn_type",
    "merchant_or_payee",
    "category",
    "confidence_score"
  ],
  "additionalProperties": false
}
```

### 6.2 System Prompt Templates

#### On-Device SLM Extraction Prompt (Literal Extractor — No Math)
```text
You are an on-device financial transaction parser. Your task is to extract structured transaction data from raw Android notification text emitted by banking apps, UPI payment systems (Google Pay, PhonePe, Paytm, CRED), credit card alerts, and bank SMS messages.

CRITICAL EXTRACTION RULES:
1. Extract the exact numerical amount literal present in the text. DO NOT perform any mathematical calculations, splits, or currency conversions.
2. Clean merchant/payee names (e.g. "UPI/SWIGGY/PAYTM/1234" -> "Swiggy").
3. Determine if it is a DEBIT (money spent/withdrawn) or CREDIT (money received/refund).
4. Assign the most accurate category from the allowed enum.
5. Provide a realistic confidence_score between 0.00 and 1.00:
   - 0.90+: Clear merchant with unambiguous category (e.g. "Swiggy", "Uber", "Netflix").
   - 0.70-0.89: Clear debit/credit, known merchant, but broad category.
   - Below 0.70: Generic personal transfer (e.g. "Paid ₹500 to Ramesh Kumar"), ambiguous format, or unclear purpose.
6. Output strict JSON matching the schema. No markdown outside JSON.
```

#### On-Device Guardrailed Spend Narrator Prompt (Zero-Arithmetic Rule)
```text
You are a personal finance assistant running locally on the user's device. You are provided with a verified set of pre-calculated spend facts derived deterministically from the local database.

PRE-CALCULATED FACTS (DO NOT MODIFY OR RECALCULATE ANY NUMBERS):
- Target Category: {{CATEGORY_NAME}}
- Current Month Spend: {{CURRENT_MONTH_SPEND}}
- Previous Month Spend: {{PREVIOUS_MONTH_SPEND}}
- Spend Delta: {{DELTA_AMOUNT_AND_PERCENTAGE}}
- Primary Driver: {{TOP_MERCHANT_NAME}} ({{TOP_MERCHANT_ORDER_COUNT}} transactions totaling {{TOP_MERCHANT_SPEND}})

MANDATORY RULES:
1. DO NOT perform any mathematical operations, additions, subtractions, or percentage calculations.
2. DO NOT alter, round, or estimate any numbers. Use ONLY the exact strings provided above.
3. Write a concise, natural 2-sentence summary highlighting the spending change and main driver.
```

---

## 7. UI/UX & Component Specifications

### 7.1 Information Architecture & Navigation
The application uses Jetpack Compose with a persistent `NavigationBar` (Bottom Navigation) featuring 4 top-level destinations:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Bottom Navigation Bar                           │
├───────────────┬────────────────┬──────────────────────┬────────────────┤
│     Home      │    Insights    │        Review        │    Settings    │
│  (Feed/List)  │ (Charts/AI)    │ (Badge = count)      │  (Preferences) │
└───────────────┴────────────────┴──────────────────────┴────────────────┘
```

### 7.2 Screen-by-Screen Specifications

#### Screen 1: Onboarding & Permission Flow (`OnboardingScreen`)
- **Step 1: Value Proposition:** Clean illustration + copy: *"Effortlessly track every rupee from your bank and UPI notifications — zero manual entry."*
- **Step 2: 100% On-Device Privacy Badge:** Visual explainer emphasizing that **no data ever leaves this phone**. All parsing is performed locally by on-device AI.
- **Step 3: Permission Prompt:** Educational card explaining why notification access is needed, with a 1-tap deep-link to Android's notification access settings.
- **Step 4: First Sync State:** Pulsing radar visual *"Listening for your first transaction..."* transitioning to Home upon first capture.

#### Screen 2: Home — Real-Time Transaction Feed (`HomeScreen`)
- **Header Card:** Month-to-date total spend in bold typography (calculated via SQL) + month-over-month delta chip (`↓ ₹1,240 vs last month`). Tap opens `InsightsScreen`.
- **Search & Filter Bar:** Search query by merchant name, category filter chips, date-range picker, amount range slider.
- **Transaction Grouping:** Grouped chronologically by date (`Today`, `Yesterday`, `14 August 2026`).
- **Row Component (`TransactionRow`):**
  - Category icon with color-coded circular background.
  - Payee name + formatted timestamp (`3:42 PM · PhonePe`).
  - Amount formatted with currency symbol (`- ₹850.00` in Slate/White for debit; `+ ₹15,000.00` in Emerald Green for credit).
  - **Confidence Indicator:**
    - `●` Solid Slate: Auto-saved with high confidence ($\ge 0.75$).
    - `◐` Half-filled Amber: Auto-saved with medium confidence ($0.50 \le \text{score} < 0.75$). Tappable to re-tag.
    - `○` Outlined Coral: Unclarified pending review.
  - Source icon: Tiny badge indicating App Push vs SMS source.
- **Manual Entry Floating Action Button (FAB):** `+` icon positioned bottom-right for manual transaction entry fallback.

#### Screen 3: Review — Clarification Queue (`ReviewScreen`)
- **Badge Counter:** Live badge in Bottom Nav displaying count of active `needs_clarification == true` transactions.
- **Card Structure (`ClarificationCard`):**
  ```
  ┌─────────────────────────────────────────────────────────────┐
  │  ₹850.00 to Ramesh Kumar                                    │
  │  Today, 3:42 PM · via PhonePe (UPI)                         │
  │                                                             │
  │  What was this for?                                         │
  │  ┌──────────┐  ┌──────────┐  ┌──────────────┐  ┌─────────┐  │
  │  │  🍽️ Food │  │  🏠 Rent │  │ 🤝 Repayment │  │ ➕ More │  │
  │  └──────────┘  └──────────┘  └──────────────┘  └─────────┘  │
  │                                                             │
  │  ▸ Raw Notification (Tap to inspect)                        │
  └─────────────────────────────────────────────────────────────┘
  ```
- **Batching Banner:** When $\ge 3$ transactions are pending, top summary banner appears: *"3 transactions need a quick tag to balance your monthly insights."*
- **Inline Expansion:** Tapping `➕ More` displays full category grid sheet with search and custom category creation.

#### Screen 4: Transaction Detail (`TransactionDetailScreen`)
- **Header:** Full editable amount, currency, and debit/credit selector.
- **Merchant & Source:** Merchant name, original payee string, transaction source package, notification post timestamp.
- **Category Picker:** Prominent dropdown/chip grid for instant re-classification.
- **Confidence Breakdown:** Informational card detailing confidence tier (e.g. *"Auto-categorized by On-Device Gemini Nano with 96% confidence based on past Swiggy orders"*).
- **Dedup Transparency:** If merged from dual sources, displays *"Merged from Bank SMS & PhonePe Notification"* with expandable sub-cards showing both original payloads.
- **Delete / Discard Action:** Option to mark as *"Not an Expense / Spam"* to remove from analytics and add negative filter rule.

#### Screen 5: Insights & Analytics (`InsightsScreen`)
- **Narrated AI Summary (`InsightCard`):** Chat-bubble / card visual with subtle gradient border. Displays guardrailed local SLM narrative over deterministic numbers with clickable hyperlinked phrases (e.g. *"8 orders on Swiggy"* opens filtered list of those 8 transactions).
- **Categorization Confidence Tile:** First-class metric widget: *"96.4% Auto-Categorized this month"* with tap-through to monthly quality trend.
- **Category Donut Chart:** Interactive donut chart rendered via Compose Canvas; tapping any slice filters Home view to that category.
- **Month-over-Month Bar Chart:** Grouped bar chart comparing current month vs previous 3 months.
- **Subscriptions Card:** Carousel of active detected recurring subscriptions with renewal count and estimated monthly total.
- **Conversational Spend Query Bar:** Search input pinned at top: *"Ask your spend (e.g. 'How much on fuel in July?')"*.

#### Screen 6: Settings (`SettingsScreen`)
- **Notification Listener Status:** Live permission check with 1-tap re-grant deep-link.
- **On-Device AI Engine Status:** Displays active local model (e.g. *"Gemini Nano (Hardware Accelerated)"* or *"MediaPipe Gemma-3-1B (Local Engine)"*).
- **Package Whitelist Manager:** Interactive list of monitored banking/payment apps with enable/disable toggles and *"Add Custom App"* package picker.
- **Template Cache Inspector:** View all learned regex patterns with hit counts and manual delete options.
- **Data Export & Backup:** Export database to structured CSV or encrypted JSON.

---

## 8. Insights, Analytics & Natural Language Query

### 8.1 Deterministic SQL Analytics Engine
All financial aggregates are computed through Room DAO queries. Examples of core deterministic SQL queries:

```sql
-- Monthly Spend by Category
SELECT 
    c.id AS category_id,
    c.name AS category_name,
    c.color_hex AS color_hex,
    c.icon_key AS icon_key,
    SUM(t.amount) AS total_amount,
    COUNT(t.id) AS transaction_count
FROM transactions t
INNER JOIN categories c ON t.category_id = c.id
WHERE t.txn_type = 'DEBIT' 
  AND t.timestamp >= :startOfMonthEpoch 
  AND t.timestamp <= :endOfMonthEpoch
GROUP BY c.id, c.name, c.color_hex, c.icon_key
ORDER BY total_amount DESC;

-- Month-over-Month Delta
SELECT 
    COALESCE(SUM(CASE WHEN timestamp >= :curMonthStart THEN amount ELSE 0 END), 0.0) AS cur_month_total,
    COALESCE(SUM(CASE WHEN timestamp >= :prevMonthStart AND timestamp < :curMonthStart THEN amount ELSE 0 END), 0.0) AS prev_month_total
FROM transactions
WHERE txn_type = 'DEBIT';

-- Top Merchant for a specific category and date range
SELECT 
    clean_payee AS merchant_name,
    COUNT(*) AS order_count,
    SUM(amount) AS total_spend
FROM transactions
WHERE category_id = :categoryId 
  AND timestamp >= :startEpoch 
  AND timestamp <= :endEpoch
GROUP BY clean_payee
ORDER BY total_spend DESC
LIMIT 1;

-- Recurring Subscription Heuristic
SELECT 
    merchant_name,
    amount,
    COUNT(*) AS occurrence_count,
    MAX(timestamp) AS latest_timestamp,
    MIN(timestamp) AS oldest_timestamp
FROM transactions
WHERE txn_type = 'DEBIT'
  AND timestamp >= :sixMonthsAgoEpoch
GROUP BY merchant_name, ROUND(amount, 0)
HAVING COUNT(*) >= 3;
```

### 8.2 Natural Language Text-to-SQL Architecture (100% On-Device & Zero SLM Arithmetic)
```
User Query: "How much did I spend on groceries in July 2026?"
                             │
                             ▼
┌────────────────────────────────────────────────────────┐
│ On-Device Text-to-SQL Translator (Local SLM)           │
│ - SLM Task: Parse intent & output SQL query            │
│ - Schema: transactions(amount, category_id, timestamp) │
│ - SLM DOES NOT CALCULATE ANY SUMS                      │
└────────────────────────────┬───────────────────────────┘
                             │ Generated Parameterized Query:
                             │ SELECT SUM(amount) FROM transactions 
                             │ WHERE category_id = 2 
                             │   AND timestamp BETWEEN 1782864000000 AND 1785542400000;
                             ▼
┌────────────────────────────────────────────────────────┐
│ Deterministic SQLite Query Execution                   │
│ Result: 12,450.00 INR (across 14 transactions)         │
│ (100% Exact Math by SQLite)                            │
└────────────────────────────┬───────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────┐
│ UI Response Presentation (Card + Filtered List)        │
│ "You spent ₹12,450.00 on Groceries in July 2026"       │
└────────────────────────────────────────────────────────┘
```

---

## 9. Security, Privacy & Play Store Compliance

### 9.1 Play Store Policy Compliance (`BIND_NOTIFICATION_LISTENER_SERVICE`)
1. **Prominent In-App Disclosure:** Prior to requesting the notification listener permission, the app presents an explicit disclosure dialog describing:
   - Exactly what data is collected (financial notification title, text, timestamp, package name).
   - Why it is collected (automatic expense categorization).
   - Absolute guarantee that all parsing is performed **100% locally on device** with zero external transmission.
2. **Graceful Degradation:** If notification permission is denied or revoked, the app remains fully functional in manual-entry mode.
3. **Zero SMS Permissions:** The app does NOT declare `READ_SMS`, `RECEIVE_SMS`, or `READ_CALL_LOG` in `AndroidManifest.xml`.
4. **Zero Internet Dependency for Core Features:** Core financial ingestion, categorization, aggregation, and insights operate completely offline without network permissions.

---

## 10. Non-Functional Requirements (NFR) & SLAs

| Metric / Dimension | Target SLA | Verification Method |
|---|---|---|
| **Notification Capture Latency** | $< 5\text{ ms}$ on main thread | Android Profiler / TraceView |
| **Tier 0 Extraction Latency** | $< 15\text{ ms}$ | Benchmark test suite |
| **Tier 1 (Gemini Nano) Latency** | $< 400\text{ ms}$ | ML Kit GenAI telemetry |
| **Tier 1 (Local Quantized SLM) Latency** | $< 800\text{ ms}$ | MediaPipe benchmark |
| **Deterministic SQL Aggregation Latency** | $< 25\text{ ms}$ | Room SQLite profiler |
| **App Startup Time (Cold)** | $< 600\text{ ms}$ to interactive | Android Vitals benchmark |
| **Daily Battery Consumption** | $< 1.8\%$ of standard 4000mAh battery | Battery Historian analysis |
| **Extraction Accuracy (Tier 0 & Tier 1)** | $\ge 98.0\%$ on supported banking formats | Benchmark test corpus (500+ samples) |
| **Arithmetic Hallucination Rate** | **0.00%** (guaranteed by deterministic engine) | Test assertion suite |
| **Offline Functionality** | 100% of app functional offline | Airplane mode test suite |

---

## 11. Phased Implementation Roadmap & Milestones

```
Timeline (10-12 Weeks Total — 100% On-Device & Zero-Arithmetic)
├─ Phase 1: Ingestion & Baseline On-Device Pipeline [Weeks 1-3]
├─ Phase 2: Clarification Loop & Tier 0 Auto-Promotion [Week 4]
├─ Phase 3: Deterministic Insights & Analytics v1 [Weeks 5-6]
├─ Phase 4: Local SLM Engine Fallback (MediaPipe / Quantized Model) [Weeks 7-8]
├─ Phase 5: Guardrailed On-Device Narratives & Text-to-SQL [Weeks 9-10]
└─ Phase 6: Battery Optimization, Benchmark Suite & Release [Weeks 11-12]
```

### Phase 1: Ingestion & Baseline On-Device Pipeline (Weeks 1–3)
- [x] Implement `TransactionNotificationListenerService` and Android Manifest configuration.
- [x] Build `PreFilterEngine` with package whitelist and regex keyword detection.
- [x] Build `DeduplicationEngine` (180s rolling window).
- [x] Configure Room DB schemas (`transactions`, `categories`, `monitored_packages`).
- [x] Integrate ML Kit GenAI Prompt API (Gemini Nano) for literal on-device extraction.
- [x] Build Compose Home screen with real-time feed, daily grouping, and detail sheet.
- [x] Implement manual transaction entry FAB fallback.

### Phase 2: Clarification Loop & Tier 0 Auto-Promotion (Week 4)
- [x] Implement confidence scoring ($0.00 - 1.00$) in on-device extraction output.
- [x] Build `ReviewScreen` tab with pending count badge.
- [x] Build `ClarificationCard` with top 3 category chips + "Other" selector.
- [x] Create `clarification_history` table and auto-promotion into `template_cache`.
- [x] Implement 48-hour auto-decay background worker via `WorkManager`.

### Phase 3: Deterministic Insights & Analytics v1 (Weeks 5–6)
- [x] Implement deterministic Room DAO aggregators (by category, by month, M-o-M delta, top merchants).
- [x] Build `InsightsScreen` with Compose Canvas Donut chart and monthly trend bar charts.
- [x] Implement Categorization Confidence metric card (`96% auto-categorized`).
- [x] Build recurring subscription detection heuristic.

### Phase 4: Local SLM Engine Fallback (Weeks 7–8)
- [x] Integrate MediaPipe LLM Inference / llama.cpp Android JNI with a 4-bit quantized Gemma 3 1B/2B or Qwen2.5 1.5B model for non-Nano hardware.
- [x] Implement runtime hardware capability detector (routes to Gemini Nano if AICore available, else MediaPipe Local SLM).
- [x] Upgrade Clarification to interactive Android Notification Action buttons with inline reply.

### Phase 5: Guardrailed On-Device Narratives & Text-to-SQL (Weeks 9–10)
- [x] Implement `InsightCard` with guardrailed prompt injecting pre-computed fact tokens into the local SLM.
- [x] Add one-tap deep-linking from narrative claims to filtered transaction queries.
- [x] Build conversational Spend Query bar with on-device Text-to-SQL translation (SQL handles all arithmetic).
- [x] Implement anomaly detection (large spends, unfamiliar merchants).

### Phase 6: Battery Optimization, Benchmark Suite & Release (Weeks 11–12)
- [x] Conduct battery profiling using Android Battery Historian.
- [x] Implement automated benchmark suite with 500+ mock banking notification payloads.
- [x] Verify 0.00% arithmetic hallucination rate across automated testing fixtures.
- [x] Complete Play Store permission disclosure and privacy policy documentation.

---

## 12. Verification & Testing Strategy

### 12.1 Test Suite Matrix
1. **Unit Tests (`src/test/`):**
   - `PreFilterEngineTest`: Verify positive and negative keyword matching across 100+ raw notification strings.
   - `DeduplicationEngineTest`: Verify deduplication of dual SMS + Bank App pushes arriving within 30s, 120s, and rejection of events at >180s.
   - `TemplateCacheTest`: Verify regex generation, token substitution, and Tier 0 auto-promotion logic.
   - `DeterministicAggregationTest`: Verify SQL math against known transaction fixture sets (100% exact numerical match).
   - `ZeroArithmeticPromptGuardTest`: Assert that fact injection tokens format accurately and the SLM prompt contains zero mathematical requests.
2. **Instrumentation Tests (`src/androidTest/`):**
   - `NotificationListenerIntegrationTest`: Inject mock `StatusBarNotification` objects into the service and verify end-to-end DB insertion.
   - `OnDeviceModelInferenceTest`: Verify structured JSON output and latency bounds on physical test devices.
   - `ClarificationFlowTest`: Compose UI automation testing chip selection and DB update.
3. **Accuracy & Benchmark Harness:**
   - Corpus of 500 realistic anonymized notifications across Google Pay, PhonePe, Paytm, HDFC, ICICI, SBI, Axis, Cred, Amex.
   - Automated evaluation measuring:
     - Exact Amount Match Rate (Target: 100%).
     - Merchant Extraction Accuracy (Target: $>98\%$).
     - Correct Category Classification (Target: $>95\%$).
     - Confidence Calibration (Low confidence for ambiguous items: $>99\%$).
     - Arithmetic Consistency (Target: 100% exact match to SQL totals).

---

*End of Specification Document.*
