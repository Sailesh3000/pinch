# AI Expense Tracker (Android) — UI Plan v1

Companion to `notification-expense-tracker-plan.md`. This plan translates the architecture (unified notification ingestion → tiered LLM extraction → confidence-gated clarification → deterministic insights) into concrete screens, flows, and components. It's sequenced to match the existing roadmap phases, so UI work never gets ahead of what the backend can actually support.

---

## 1. Design Principles

These follow directly from the product's architectural bets — the UI should make them visible, not hide them.

1. **Confidence is a UI-first-class citizen, not a debug field.** Every transaction shows, at a glance, whether it was auto-categorized with high confidence, auto-categorized with a guess, or needs the user's input. Users should never wonder "did the app actually know this, or is it faking certainty?"
2. **Never block on AI.** Nothing waits on a model call. Transactions appear immediately (Section 5 of the backend plan: save first, clarify after). The UI's job is to make "clarify later" feel effortless, not naggy.
3. **Ask like a person, not a form.** Clarification prompts are one-tap chip choices with a free-text escape hatch — never a multi-field form for a ₹200 UPI transfer.
4. **Numbers are always deterministic; narration is always labeled.** Charts and totals come from SQL, full stop. Anywhere the LLM is narrating ("you spent 22% more..."), the UI gives it a distinct visual treatment (e.g., a chat-bubble/insight-card style, not a plain data tile) so users intuitively know which parts are "generated" vs "computed."
5. **Trust is a metric you can see.** The categorization-confidence stat (Section 8 of backend plan) isn't buried in settings — it's a visible, almost gamified indicator of the system learning the user's specific banks and habits over time.
6. **Permission asks are justified in-context, not upfront.** The notification-listener permission is sensitive; the UI should explain *why* right before asking, not as a wall of legalese on first launch.

---

## 2. Information Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Bottom Navigation (4 tabs)                                  │
├───────────┬───────────┬────────────────┬─────────────────────┤
│  Home     │  Insights │  Review         │  Settings           │
│ (default) │           │ (badge = count) │                     │
└───────────┴───────────┴────────────────┴─────────────────────┘
```

- **Home** — running transaction feed (list of all captured transactions, newest first), search/filter, monthly total at top.
- **Insights** — charts, category breakdown, LLM-narrated summaries, subscriptions/anomalies.
- **Review** — the clarification queue. Badge shows pending count. This tab only appears once Phase 2 ships; before that it can be a card on Home.
- **Settings** — permission status, privacy mode toggle (on-device vs cloud), category management, data export.

Transaction Detail and Onboarding/Permission flows are modal/stack screens reached from these tabs, not tabs themselves.

---

## 3. Onboarding & Permission Flow

This is the first real UX risk in the product — `NotificationListenerService` is a sensitive Android permission and users (correctly) hesitate before granting broad notification access. The flow needs to earn the ask.

**Screen sequence:**

1. **Welcome** — one-line value prop ("Auto-track spending from your bank and UPI notifications — no manual entry"). No permission ask yet.
2. **How it works** — a simple 3-step visual: *Notification arrives → App reads only finance-related ones → Transaction appears categorized.* Explicitly states what it does *not* do (read personal messages, social notifications, OTPs it doesn't need, etc.) — this pre-empts the most common hesitation.
3. **Privacy choice** (ties to backend plan Section 7/11) — a simple two-card choice, not a settings toggle buried later:
   - **"Keep everything on this device"** (Tier 0/1 only, cloud disabled) — recommended badge if device supports Gemini Nano.
   - **"Allow cloud fallback for unfamiliar formats"** (better accuracy on new banks, sends only unrecognized notification text, never raw data at rest) — with a one-line explainer of what "fallback" means.
   - This choice should be changeable later from Settings, not a one-time fork.
4. **Grant permission** — deep-links to Android's notification access settings screen (this is an OS-level screen the app can't customize — the in-app screen right before it does the persuasion work).
5. **Confirmation + first sync state** — "Listening for transactions" empty state with a subtle pulse/animation, transitioning to the Home feed as soon as the first transaction lands.

If permission is denied or later revoked, Home should show a persistent (but dismissible-per-session) banner rather than a blocking screen — the app should degrade gracefully to manual entry.

---

## 4. Home — Transaction Feed

**Purpose:** the default screen; primary "is this working" surface.

**Layout (top to bottom):**
- **Month summary header** — total spent this month, small delta vs. last month ("↓ ₹1,240 vs. last month"), tap to jump to Insights.
- **Filter/search bar** — by category, merchant, amount range, source (notification vs. SMS-via-notification), date range.
- **Transaction list**, grouped by day, each row:
  - Merchant/payee name (best available; fall back to raw payee string if unresolved)
  - Category icon + label
  - Amount (color-coded: debit vs. credit)
  - **Confidence indicator** — a small dot or icon, three states:
    - ● solid = high confidence, auto-saved silently
    - ◐ half-filled = low confidence, auto-saved with best guess, tappable to correct
    - ○ outlined/amber = pending clarification (only if Review tab hasn't caught it yet)
  - Source badge (tiny icon distinguishing "app notification" vs "SMS") — useful for debugging trust, low visual weight.
- **Tap a row → Transaction Detail** (Section 6).
- **Empty state** (before first transaction arrives): reassurance copy + link to "why isn't anything showing up" troubleshooting (permission check, whitelist explainer).

---

## 5. Review — Clarification Queue

Directly implements backend plan Section 6. This is the screen that makes or breaks trust in the "ask, don't guess" pitch.

**Two presentation modes, matching the roadmap's phased rollout:**

- **Phase 2 (simple):** a plain in-app list, same visual language as Home but filtered to `needs_clarification = true`. Each row expands inline to show the chip picker.
- **Phase 4 (interactive):** Android notification actions/inline reply — the clarification can be answered directly from the notification shade without opening the app, for the true "one-tap" experience described in the backend plan.

**Per-item clarification card:**
```
┌───────────────────────────────────────────┐
│  ₹850 to Ramesh K.                         │
│  Aug 14, 3:42 PM · via PhonePe             │
│                                             │
│  What was this for?                        │
│  [ Food ]  [ Rent ]  [ Friend repay ]  [+] │
│                                             │
│  ⋯ raw notification text (expandable)      │
└───────────────────────────────────────────┘
```
- Chips are the user's most-used categories first (personalized ordering), plus a generic "Other" that opens free text.
- The raw notification text is available but collapsed by default — useful for power users who want to verify, not needed by default.
- **Batching:** when multiple items are pending, show an end-of-day summary card ("3 transactions need a quick tag") rather than surfacing them one at a time — matches the backend plan's anti-fatigue design.
- **Timeout state:** items older than the decay window (e.g. 48h) visually shift from "○ pending" to "auto-tagged, tap to fix" and drop out of the badge count, so the queue never feels like accumulating debt.

---

## 6. Transaction Detail

Reached from any list row. Single scrolling screen, not a modal — corrections should feel low-friction and permanent.

- Amount, merchant, date/time, source app, category (editable — tapping it always opens the same category picker used in Review, regardless of how the transaction was originally categorized; per backend plan Section 6 implementation notes, correction must always be available).
- Confidence badge with a one-line explanation on tap ("Auto-categorized with high confidence based on past PhonePe transactions").
- Raw notification text, collapsed by default.
- Duplicate-transaction indicator if this was merged from two sources (bank app + SMS notification) — small "merged from 2 sources" tag, expandable to show both original texts. Gives users a way to catch dedup mistakes.
- Delete/mark-as-not-a-transaction action, for pre-filter false positives.

---

## 7. Insights

Implements backend plan Section 8. Visually, this screen should clearly separate **computed** content from **narrated** content (Design Principle 4).

**Layout:**
- **Narrated summary card(s)** at the top — chat-bubble or "assistant note" visual treatment, distinct from chart tiles below. E.g., *"You spent 22% more on food delivery this month, mostly driven by 6 orders from Swiggy in the last week."* Tapping a summary can deep-link to the filtered transaction list backing that claim — this is important: every LLM-narrated claim should be one tap from its underlying deterministic data, so it never feels like an unverifiable assertion.
- **Category breakdown chart** — donut or horizontal bar, tap a slice to filter Home.
- **Month-over-month trend** — simple line/bar chart.
- **Subscriptions & recurring payments card** — detected recurring charges with next-renewal date, total this cycle.
- **Budget nudges** (once budgets exist) — inline warning-style card, not a separate screen: "On track to exceed dining budget by Friday."
- **Anomaly flags** — unusually large or unfamiliar-merchant transactions, same visual treatment as clarification-style alerts.
- **Ask your spend (NL query box)** — a simple search-style input pinned near the top or as a floating action: "How much did I spend on Ubers in June?" Returns a short computed answer + optional chart, not free-form prose, to reinforce that it's a query tool, not a chatbot.
- **Categorization confidence stat** — small persistent tile, e.g. "94% auto-categorized this month" with a tap-through to a simple trend of that percentage over time. This is the visible "quality signal" called out in backend plan Section 8 — treat it as a first-class metric tile, not a settings-page footnote.

---

## 8. Settings

- **Permission status** — live check of notification-listener access, with a re-grant deep link if revoked.
- **Privacy mode** — the on-device-only vs. cloud-fallback choice from onboarding, changeable here, with the same plain-language framing (not just a technical toggle).
- **Category management** — add/rename/merge/archive categories; view/edit the per-user few-shot cache indirectly through "your corrections" (a list of past clarification answers, editable/deletable — gives users visibility and control over what's training the system).
- **Bank/package whitelist** — which apps are being monitored, with an add/remove list; surfaces the pre-filter whitelist from the backend plan in a way users can audit and adjust.
- **Data export** — CSV/JSON export of transactions.
- **Notification digest preferences** — how clarification prompts are delivered (in-app only vs. system notification actions), batching frequency.

---

## 9. Component Inventory (for implementation)

Reusable Compose components implied by the screens above — worth building once, not per-screen:

| Component | Used in | Notes |
|---|---|---|
| `TransactionRow` | Home, Review (collapsed state) | Confidence dot, source badge, category icon — parameterize confidence state |
| `ConfidenceBadge` | TransactionRow, Detail, Insights stat tile | Three visual states + tap-to-explain tooltip |
| `CategoryChipPicker` | Review card, Detail, onboarding-style corrections | Personalized chip ordering + free-text fallback |
| `InsightCard` (narrated) | Insights | Distinct visual language from `ChartCard`; deep-links to filtered `TransactionList` |
| `ChartCard` (computed) | Insights | Wraps chart lib (MPAndroidChart/Compose-native per backend plan Section 9) |
| `ClarificationQueueBadge` | Bottom nav | Pending count, decays as items time out |
| `PermissionStatusBanner` | Home (degraded state), Settings | Same component, different dismiss behavior |
| `RawNotificationExpander` | Detail, Review card | Collapsed-by-default raw text, monospace |

---

## 10. UI Rollout — Mapped to Backend Roadmap

Keeping this explicit so UI work never outpaces what's actually wired up:

| Backend Phase | UI Scope |
|---|---|
| **Phase 1** — Ingestion + baseline extraction | Onboarding/permission flow, Home feed (list/detail only), manual category correction always available. No Review tab yet — low-confidence items just show the ◐ indicator inline. |
| **Phase 2** — Clarification loop v1 | Review tab (simple list mode), chip picker, "needs review" badge. No notification-action prompts yet. |
| **Phase 3** — Insights v1 | Insights tab: charts + monthly summary only (no narrated cards yet), categorization-confidence stat tile introduced. |
| **Phase 4** — Hybrid LLM pipeline | Upgrade Review to interactive notification actions/inline reply. Privacy-mode setting becomes meaningful (on-device vs. cloud actually differ in behavior now). |
| **Phase 5** — Agentic insights | Narrated `InsightCard`s, subscription/budget/anomaly cards, NL query box. |
| **Phase 6** — Robustness & scale | Bank/package whitelist management surfaced in Settings; multi-language category labels if expanding beyond one market. |

---

## 11. Open UX Questions to Resolve Early

- Should the confidence indicator use color (risk: color-blind accessibility, and "amber = warning" framing might make users distrust a system that's actually working as designed) or shape/fill only? Recommend shape/fill primary, color as secondary reinforcement only.
- How prominent should the "merged from 2 sources" dedup transparency be — enough to build trust without cluttering every row (recommend: Detail screen only, not the list).
- Where does manual transaction entry live for the (Section 4 backend) fallback case where notification capture fails? Likely a simple "+" FAB on Home, always available regardless of AI pipeline status.
- Should Review's badge count include auto-tagged-after-timeout items, or only true pending ones? Recommend: badge = pending only, timed-out items live in a separate "recently auto-tagged" filter so the badge stays an accurate to-do count.
