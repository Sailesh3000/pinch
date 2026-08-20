# AI Expense Tracker (Android) — Project Plan v2

**Scope decision:** Android-only. iOS blocks third-party read access to both notifications and SMS content — there's no policy-compliant workaround, so this plan targets Android exclusively. See Appendix A for what a separate iOS product would need to look like if you revisit this later.

## 1. Executive Summary

The core idea — capture bank/payment notifications and SMS, extract transaction data with an LLM, categorize and surface insights — is a validated pattern with several open-source precedents. None of them are "done." Most stop at extraction + basic categorization. The real differentiation opportunity is in the **insights/agent layer**, the **on-device vs cloud LLM tradeoff**, **cross-bank/cross-region robustness**, and a **human-in-the-loop clarification step** for the cases even a good LLM can't confidently resolve — all of which existing projects punt on.

---

## 2. Existing Open Source Landscape

| Project | Platform | Data Source | AI Approach | Notes |
|---|---|---|---|---|
| [atick-faisal/Expense-Tracker-Android](https://github.com/atick-faisal/Expense-Tracker-Android) | Android, Jetpack Compose | SMS (not notifications) | Cloud Gemini API for categorization | Closest match to your idea. Explicitly bank-locked (Qatar banks only: QNB, QIB, CBQ, Doha Bank) — hardcoded parsing rules per bank. Uses RAG. Apache-2.0. |
| [wealth-wave/Auto-Expense-Tracker](https://github.com/praslnx8/Expense-Tracker) | Android | SMS | Regex/rule-based, no LLM | Shows the "traditional" non-AI baseline you're improving on. |
| Saumya-Rai/Expense-Tracker | Android | SMS | Regex/pattern matching | Same category, simpler. |
| Various "notification-parsing" Compose apps (e.g. privacy-first Jetpack Compose trackers) | Android | Notifications | Rule-based | Uses `NotificationListenerService` but no LLM — closest to your data pipeline, missing the AI layer. |
| Jibify (Play Store, blog-documented) | Android | Voice + transactions | **On-device** Gemini Nano via ML Kit Prompt API | Good reference for the on-device pattern — auto-categorizes and parses natural voice input entirely locally. |
| MyExpenses, Cashew, Fintrack, Moneytopia, etc. | Android/Flutter | Manual entry | None | Mature, well-built manual trackers — good for UI/UX and data-model inspiration, not for AI or ingestion. |

**Pattern across all of them:** SMS is used far more often than notifications (SMS is easier to read via `SMS_READ` permission and is more standardized). Notification-based ingestion is less common and less mature — that itself is a gap, because notifications cover UPI apps, wallets, credit cards without SMS alerts, and non-SMS markets. Almost nobody handles **multi-bank, multi-region, multi-language** parsing well — they all hardcode a handful of banks' message formats.

---

## 3. Where You Can Meaningfully Differentiate

Ranked by impact vs. effort:

1. **Generalized extraction instead of bank-specific regex.** Use the LLM as the parser itself (structured output), not just a categorizer bolted onto regex. This is the single biggest differentiator — it's why every existing project is locked to 3-4 banks.
2. **Hybrid on-device + cloud LLM routing.** Use a small on-device model (Gemini Nano / Apple Intelligence / a distilled local model) for simple, well-known notification formats, and fall back to a cloud LLM only for ambiguous/new formats. Nobody in the list above does this — they're all one or the other.
3. **Insight/agent layer, not just categorization.** "You spent ₹4,200 more on food delivery this month than your 3-month average" or "3 subscriptions renew this week, totaling ₹1,800" — proactive, agentic summaries rather than passive charts. This is where an LLM genuinely adds value beyond extraction.
4. **Privacy-first architecture as a selling point.** Since financial notification content is sensitive, doing extraction on-device (or with strict redaction before any cloud call) is a real differentiator you can market, not just an engineering choice.
5. **Self-correcting categorization via feedback loop.** Let user corrections fine-tune a per-user prompt/few-shot cache or a lightweight local classifier over time, instead of static categories.
6. **Human-in-the-loop clarification instead of silent guessing.** When the model is genuinely unsure (ambiguous merchant, split-purpose payment, an unfamiliar format), surface a lightweight prompt asking the user directly rather than silently mis-categorizing. Existing projects all fail silently — they either guess wrong or drop the transaction. Turning uncertainty into a one-tap question is a real trust-builder and a data-quality win (see Section 6).

---

## 4. Ingestion Strategy — Unified Notification Listener (Android)

**Single capture point, two data sources.** Rather than building separate SMS and notification pipelines, use `NotificationListenerService` as the *only* ingestion mechanism, for both:

- **App notifications** from banks, UPI apps (Google Pay, PhonePe, Paytm), credit cards, wallets — captured directly.
- **SMS-originated bank alerts** — captured indirectly, via the notification that Android's default Messages app posts when an SMS arrives. As long as the user has SMS notifications enabled (the default), your listener sees the full SMS text in that notification's payload.

**Why not request `READ_SMS`/`RECEIVE_SMS` directly:** Google restricts these permissions on the Play Store to apps whose *core function* is SMS handling (default SMS/dialer apps), enforced via a mandatory Permissions Declaration Form. A general expense tracker is very unlikely to qualify, and rejection risk is real. Going through the notification listener sidesteps this entirely — it's a single, well-precedented permission (`BIND_NOTIFICATION_LISTENER_SERVICE`) that every app in the landscape review already relies on.

**When you'd still need direct SMS access:** only if you hit real gaps — e.g., a user has disabled SMS notifications, or a specific OEM's messaging app doesn't post a system notification for incoming texts. Treat this as a documented fallback/edge case, not a default part of the architecture. If you ever need it, it requires the separate Play Store review process above.

**iOS is out of scope for this plan.** Apple blocks third-party read access to both notification content from other apps and SMS/iMessage content, with no general policy-compliant workaround (see Appendix A for the narrow exceptions and what an iOS-specific product would require). This plan is Android-only.

---

## 5. High-Level Architecture (Android)

```
┌─────────────────────────────────────────────────────────────────┐
│  NotificationListenerService                                    │
│  Captures BOTH: bank/UPI/wallet app notifications                │
│                 AND default Messages app notifications (= SMS)  │
│  Payload: package name, title, text, timestamp                  │
└───────────────────────────┬───────────────────────────────────┘
                             │ raw notification
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  Pre-filter (cheap, on-device, no LLM)                          │
│  - Whitelist known finance-app package names + Messages app     │
│  - Regex fast-path for keywords: "debited", "spent", "credited" │
│  - Drop everything else immediately (privacy + cost)            │
└───────────────────────────┬───────────────────────────────────┘
                             │ candidate transaction text
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  Dedup Layer                                                     │
│  Same transaction can arrive twice (e.g. bank app notif AND     │
│  its SMS alert). Key on (amount, rough merchant/ref text,       │
│  timestamp window ~2-5 min) → merge, keep richer source.        │
└───────────────────────────┬───────────────────────────────────┘
                             │ deduped candidate
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  LLM Extraction Layer (structured output + confidence score)    │
│  Router: known-format cache hit? → template parse (no LLM call) │
│          else → on-device SLM → if low confidence → cloud LLM   │
│  Output: {amount, currency, merchant, category, txn_type, date, │
│           confidence}                                            │
└───────────────────────────┬───────────────────────────────────┘
                             │
              ┌──────────────┴──────────────┐
      confidence ≥ threshold        confidence < threshold
              │                              │
              ▼                              ▼
┌─────────────────────────────┐   ┌─────────────────────────────────┐
│  Auto-save to DB             │   │  Clarification Queue (Section 6) │
│  (silent, no user action)    │   │  User answers a quick prompt →   │
│                               │   │  answer feeds back into DB AND   │
│                               │   │  into the template/few-shot cache│
└───────────────────────────┬─┘   └───────────────┬───────────────────┘
                             │                      │
                             ▼                      ▼
┌─────────────────────────────────────────────────────────────────┐
│  Local DB (Room / SQLite) — source of truth, always offline-safe│
└───────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  Insights Engine                                                 │
│  - Deterministic aggregates (SQL: totals, by-category, by-merchant)│
│  - LLM-generated natural-language summaries on top of aggregates │
│  - Anomaly/trend detection (rule-based + optional LLM narrative) │
└─────────────────────────────────────────────────────────────────┘
```

**Key design principle:** never let the LLM be the source of truth for numbers. Use the LLM to *extract* and to *narrate*, but do all arithmetic (sums, averages, trend %) in deterministic code against the database. This avoids the classic "LLM hallucinated my total spend" failure mode. The **confidence score** the extraction step emits alongside its structured output is what drives the fork into auto-save vs. clarification — treat it as a required field in the extraction schema, not an afterthought.

---

## 6. Human-in-the-Loop Clarification (When the LLM Is Unsure)

This is the feature that turns "silent wrong guesses" into "trustworthy assistant." When extraction confidence is low, don't guess — ask.

### What triggers a clarification prompt
- **Ambiguous category:** amount and merchant are clear, but the spend type isn't (e.g. a generic UPI transfer to an individual's name — could be rent, a friend repayment, or a gift).
- **Unrecognized/garbled format:** a bank or app whose notification shape you haven't seen before, where the model's own confidence score comes back low.
- **Split-purpose signals:** notification text suggests a bundled payment (e.g., a single large UPI transfer that historically has been split across categories by this user).
- **Conflicting dedup candidates:** two notifications look like they might be the same transaction, but the merge heuristic isn't confident.

Confidence should be a first-class field the extraction model outputs (e.g., 0–1 score, or discrete low/medium/high), not something you infer after the fact — ask for it explicitly in the structured-output schema.

### What the prompt looks like
Keep it minimal — this is a tap, not a form:
- A **notification-style prompt** (Android supports interactive notification actions/inline replies) or a small in-app card that appears next time the app is opened: *"₹850 to Ramesh K. — what was this for?"* with 3-4 quick category chips (Food, Rent, Transfer to friend, Other) plus a free-text option.
- **Don't block on it.** Save the transaction immediately with a placeholder category ("Uncategorized" / best-guess) so nothing is lost or delayed; the clarification just refines it after the fact.
- **Batch when possible.** If several low-confidence transactions are pending, surface them together as a short end-of-day "3 transactions need a quick tag" review rather than interrupting the user notification-by-notification.
- **Respect a timeout/decay.** If the user never responds, fall back to the model's best guess after some period (e.g., 48 hours) rather than leaving it stuck in limbo forever — surface it in the UI as "auto-categorized, tap to correct" instead.

### Why this matters beyond UX polish
- **It's a training signal, not just a fix.** Every clarification answer should feed back into the Tier 0 template cache and/or a per-user few-shot example set (see Section 7), so the same ambiguous pattern from the same source doesn't need to be asked again. This is what makes the system get *better* for that specific user over time, which none of the reviewed open-source projects do.
- **It's honest about LLM limits.** Rather than presenting a categorization with false confidence, surfacing uncertainty (and asking) is more trustworthy — especially for a finance app, where a silently wrong category (e.g., a rent payment logged as "shopping") quietly corrupts every insight built on top of it.
- **It bounds your error rate cheaply.** You don't need the model to be perfect — you need it to *know when it's not sure*. That's a much easier bar to hit than "always correct," and it's the difference between an app people trust and one they abandon after a few miscategorized months.

### Implementation notes
- Store clarification answers with the original notification text as a (input → correct label) pair — this is exactly the training data you'd want if you ever fine-tune a lightweight local classifier down the line.
- Cap how often you ask — if a specific bank/package is generating a lot of low-confidence extractions, that's a signal to invest in a better template for it (Tier 0), not to keep prompting the user indefinitely.
- Make correction always available, not just reactive — every transaction in the list view should be one tap away from "change category" regardless of whether it was auto-saved or clarified, since even high-confidence extractions will occasionally be wrong.

---

## 7. LLM Strategy — Cloud vs On-Device (SLM), and How to Combine Them

### Why this decision matters here specifically
You're processing every notification the user gets, potentially dozens a day, containing sensitive financial data. That makes latency, cost-per-call, offline reliability, and privacy all first-order concerns — more so than in a typical chat app.

### Option A: Cloud LLM only (e.g., Claude Haiku / Gemini Flash / GPT-4o-mini for extraction)
- **Pros:** Best raw accuracy on messy/novel notification formats, easiest to build (just a structured-output API call), works for any bank without local model management, easy to iterate on prompts.
- **Cons:** Requires network for every transaction (breaks offline capture — though you can queue and batch), recurring per-call cost at scale, sends financial notification text off-device (real privacy concern and a hard sell for a "privacy-first" pitch), added latency (typically 300ms–2s per call).
- **Use structured output / tool-calling / JSON mode** so you get `{amount, merchant, category}` reliably instead of parsing free text.

### Option B: On-device SLM only (Gemini Nano via Android ML Kit GenAI, or a local GGUF model via llama.cpp/MediaPipe)
- **Pros:** Zero marginal cost, works offline, notification content never leaves the device (strong privacy story), low latency once the model is loaded, no server infra needed.
- **Cons:** Only available on capable hardware — Gemini Nano requires Pixel 8+/Galaxy S24+ class devices with AICore; older/budget Android phones (a huge share of your likely user base if you're building for India, for instance) won't have it. Smaller models are less reliable on messy, unfamiliar notification formats. Context window is small (Gemini Nano: ~4K tokens — plenty for a notification, but not for few-shot-heavy prompts). A **Structured Output API** for the Prompt API is on the way (announced at Google I/O '26) but wasn't broadly available as of the model's training cutoff — check current docs before depending on it.
- Real-world reference: **Jibify** (a shipped app) uses exactly this pattern — ML Kit's on-device Prompt API for transaction categorization and voice parsing, with zero server round-trip.

### Option C (recommended): Hybrid tiered pipeline
1. **Tier 0 — Template cache (no model at all).** Once you've successfully parsed a notification format from a given bank/package, cache the pattern (a lightweight extraction template or a few-shot example keyed by package name + message shape). Most repeat notifications from the same bank hit this tier — zero cost, instant, no model needed. This is essentially what makes the *hardcoded regex* approach in existing repos usable at all; you're just making it self-updating instead of manually maintained.
2. **Tier 1 — On-device SLM.** For anything not in the template cache, try the local model first (Gemini Nano on supported devices, or a small quantized model like Phi-3.5-mini/Gemma-3-1B/Qwen2.5-1.5B via `llama.cpp`/MLC-LLM/MediaPipe LLM Inference on unsupported devices). Ask it for structured JSON with a confidence self-assessment.
3. **Tier 2 — Cloud LLM fallback.** Only escalate to a cloud call (Haiku/Flash-class model, cheap and fast) when the on-device result is low-confidence, malformed, or the device has no local model support at all. Batch these where possible (e.g., process the day's unrecognized notifications together) to cut cost and let the user be offline most of the time.
4. **Learn forward:** once a cloud call successfully parses a new bank's format, promote it into the template cache (Tier 0) so it's never sent to the cloud again for that bank/package.

This tiered design directly targets the two big weaknesses in existing projects: bank-lock-in (solved by LLM-based extraction instead of hardcoded regex) and cost/privacy/offline concerns (solved by defaulting to local/cached processing and only using cloud as a fallback).

### Concrete model choices right now
- **On-device:** Gemini Nano via ML Kit GenAI Prompt API (Android, Pixel 8+/S24+ class hardware) is the most turnkey option and has a documented structured-output path forming. For broader device coverage, a small quantized open model (Gemma 3 1B/2B, Qwen2.5 1.5B/3B, Phi-3.5-mini) via MediaPipe LLM Inference or `llama.cpp` gives you control over which devices you support, at the cost of managing model download/updates yourself.
- **Cloud fallback:** any fast/cheap structured-output-capable model (Claude Haiku, Gemini Flash, GPT-4o-mini class) — pick based on your existing infra/API access rather than the model itself; accuracy differences at this task size are small.

---

## 8. Insights Layer (the actual differentiator)

Once transactions are structured and stored, treat the LLM as a **narrator over deterministic data**, not a calculator:

- Compute all numbers (totals, category breakdowns, month-over-month deltas, merchant frequency) in SQL/Kotlin against the local DB.
- Feed those computed aggregates (not raw notifications) to an LLM to generate natural-language summaries: *"You spent 22% more on food delivery this month, mostly driven by 6 orders from Swiggy in the last week."*
- Add proactive/agentic touches that competitors mostly skip:
  - Subscription/recurring-payment detection and renewal reminders.
  - Budget-threshold nudges ("you're on track to exceed your dining budget by Friday").
  - Anomaly flags (unusually large or unfamiliar-merchant transaction).
  - Natural-language query over your own spend ("how much did I spend on Ubers in June?") — this is a good use for a small on-device model doing text-to-SQL against your local schema, since it's low-risk (read-only, deterministic verification possible) and keeps the differentiator local/free.
- Surface a lightweight "categorization confidence" stat over time (e.g., "94% auto-categorized this month, 6% you tagged manually") — it makes the clarification system feel like a visible quality signal rather than a nagging chore, and gives you a concrete metric to improve against.

---

## 9. Suggested Tech Stack

- **Android app:** Kotlin, Jetpack Compose, Room (local DB), Hilt (DI) — this mirrors what the existing best-in-class repos (atick-faisal's, Jibify) already use, so you can study their code structure directly.
- **Notification capture:** `NotificationListenerService` (covers both app notifications and SMS-via-Messages-app notifications) + a foreground service or WorkManager-based batching layer for reliability.
- **On-device inference:** ML Kit GenAI Prompt API (Gemini Nano) as primary; MediaPipe LLM Inference API or `llama.cpp` (via JNI) as a fallback for non-Nano devices if you want broader coverage.
- **Cloud fallback:** any provider's structured-output/tool-use endpoint, called only for Tier 2 escalations; queue-and-batch to control cost.
- **Local storage:** Room/SQLite for transactions (with a `confidence` and `needs_clarification` field per row); a small key-value or JSON store for the Tier 0 template cache; a `clarification_answers` table storing (raw text → user-chosen category) pairs for reuse as few-shot examples.
- **Clarification UI:** Android's notification action buttons / inline reply for the "tap to tag" quick prompt, plus an in-app review card for batched end-of-day clarifications.
- **Charts/insights UI:** existing chart libs used by the manual-entry apps above (MPAndroidChart or Compose-native charting) — no need to reinvent this part.

---

## 10. Suggested Roadmap

**Phase 1 — Ingestion + baseline extraction (2-3 weeks)**
- `NotificationListenerService` capture (app notifications + Messages-app SMS notifications), package whitelist, keyword pre-filter.
- Basic dedup (amount + timestamp-window heuristic).
- Cloud-LLM-only structured extraction, including a confidence field, to validate the concept end-to-end (skip on-device complexity initially).
- Local DB + basic list/detail UI, with manual category correction always available.

**Phase 2 — Clarification loop v1 (1 week)**
- Wire low-confidence extractions to a simple in-app "needs review" list (skip notification-action prompts initially — ship the simplest version first).
- Store clarification answers; use them to override future identical/near-identical notifications from the same source.

**Phase 3 — Insights v1 (1-2 weeks)**
- Deterministic aggregates (spend by category/merchant/time).
- Simple charts, monthly summary screen, categorization-confidence stat.

**Phase 4 — Hybrid LLM pipeline (2-4 weeks)**
- Add Tier 0 template cache, seeded partly from Phase 2's stored clarification answers.
- Integrate on-device SLM (Gemini Nano first, since it's the most turnkey), route by confidence.
- Add cloud fallback only for genuinely novel/low-confidence formats.
- Upgrade clarification UI to interactive notification actions/inline reply for a true one-tap experience.

**Phase 5 — Agentic insights (2-3 weeks)**
- Natural-language summaries and Q&A over spend data.
- Subscription detection, budget nudges, anomaly flags.

**Phase 6 — Robustness & scale**
- Expand bank/package coverage, handle multi-language notifications, refine dedup heuristics, telemetry on where the pipeline drops to cloud or triggers clarification most often (tells you what to add to the template cache next).

---

## 11. Risks & Things to Decide Early

- **Play Store policy:** `NotificationListenerService` access is a sensitive permission; Google requires a clear in-app disclosure and justification, and review can be strict for finance-adjacent apps. Budget time for this.
- **Privacy positioning:** decide up front whether you're marketing this as "on-device/private" (constrains you to Tier 0/1 as the default path, cloud only as rare fallback with explicit user consent) or "cloud-powered, more accurate" (simpler to build, weaker privacy story).
- **Device fragmentation:** a large share of Android users won't have Gemini Nano-capable hardware; have a real plan (small local model or cloud fallback) rather than assuming Nano everywhere.
- **Clarification fatigue:** if too many transactions trigger a prompt, users will start ignoring them (or uninstall). Track your low-confidence rate as a core quality metric and treat a high rate as a bug to fix in the extraction pipeline, not a permanent state.

---

## Appendix A — If You Ever Revisit iOS

Not in scope for this plan, but for reference: iOS blocks third-party read access to both notification content from other apps and SMS/iMessage content, with no general policy-compliant workaround (the narrow exceptions — OTP autofill, the spam-filter extension for unknown senders, and the EU-only `TelephonyMessagingKit` for messaging-app replacements — don't cover a finance app reading bank alerts). A separate iOS product would need a fundamentally different ingestion method: email receipt parsing (Gmail/Outlook API), bank aggregator APIs (Plaid-style, or India's Account Aggregator framework), manual/photo receipt capture with LLM OCR, or a share-sheet extension where the user manually shares a transaction SMS/email into the app. None of these are drop-in replacements for the Android pipeline in this plan — treat iOS as a separate product effort if you pursue it.
