# Privacy Policy for Pinch — Expense Tracker

**Last Updated:** 2026-09-17

## Overview

Pinch is a privacy-first, 100% on-device expense tracking application. Your
financial data never leaves your device. This privacy policy explains what
data we access, how it is used, and what is never collected.

## Data We Access

| Data Type | Purpose | Storage |
|-----------|---------|---------|
| **Notification content** from payment apps (GPay, PhonePe, Paytm, bank apps) | Extract transaction details (amount, merchant, category) | On-device, encrypted |
| **SMS content** from bank SMS senders | Extract transaction details from bank alerts | On-device, encrypted |

## What We NEVER Collect

- **No personal identifiers** (name, email, phone number, device ID)
- **No financial account numbers** — we extract merchant names and amounts only
- **No location data**
- **No contacts, call logs, or SMS history content**
- **No server-side transmission of your transactions** — everything stays on-device

## Permissions Explained

### Notification Listener Access

**Why we need it:** To read transaction notifications from payment apps
(Google Pay, PhonePe, Paytm) and bank apps in real time. Android's official
`BIND_NOTIFICATION_LISTENER_SERVICE` API is the only user-granted mechanism a
third-party app can use to observe these notifications. See
[NOTIFICATION_ACCESS_JUSTIFICATION.md](NOTIFICATION_ACCESS_JUSTIFICATION.md)
for the full disclosures.

**What we do with it:**
- Parse notification text to extract amount, merchant, and category
- Store extracted data locally in the app's SQLCipher-encrypted database
- **Never transmit notification content to any server**

**How to revoke:** Settings → Apps → Pinch → Notification access (or
Settings → Notifications → App notification access).

### SMS Read Access (`android.permission.READ_SMS`)

**Why we need it:** To read bank transaction SMS alerts from whitelisted bank
senders and convert them to transactions.

**What we do with it:**
- Parse SMS text from whitelisted bank senders only
- Extract transaction details using on-device regex patterns
- **Never transmit SMS content to any server**

**How to revoke:** Settings → Apps → Pinch → Permissions → SMS → Deny.

### Internet Access (`android.permission.INTERNET`)

**Why we need it:**
- (Primary) Installing the app delivers the on-device AI model bundled with
  the app via **Google Play Asset Delivery** — no download needed on the
  common path.
- (Fallback) A one-time optional download of the on-device AI model from our
  model hosting (Hugging Face / GitHub mirror) when the bundled copy isn't
  available (e.g., sideloaded installs).
- (Crash reporting) Optionally, encrypted crash diagnostics delivered to
  Sentry when a crash occurs (see below).

## On-Device AI Processing

All AI inference happens locally on your device:

1. **Gemini Nano** (via Google AI Core) — on-device transaction categorization
2. **MediaPipe** (Qwen2.5-0.5B) — bundled/on-device model inference when Gemini
   Nano is unavailable
3. **Deterministic Regex** — offline fallback that needs no model at all

**No transaction data is sent to cloud AI services.** All inference happens
locally on your device.

## Crash Reporting (Opt-Configured)

Pinch uses **Sentry** software for crash reporting. **By default crash
reporting is disabled** and no `SENTRY_DSN` is bundled. If future builds ship
with a DSN configured, then — and only then — Sentry may receive crash
diagnostics with the following safeguards:

- A financial-data scrubber strips amount/merchant/UPI/bank/account-looking
  tokens from all events before they leave the device.
- App Attachments, screenshots, and Personal Identifiable Information (PII)
  capture are disabled.
- No transaction or notification content is ever reported.

## Data Storage & Encryption

- **Database:** SQLCipher-encrypted SQLite database stored in the app's
  private directory. The encryption passphrase is generated per-install and
  protected by the Android Keystore.
- **AI Model:** Bundled via Play Asset Delivery (install-time) and/or stored in
  the app's private directory.
- **Backup:** Android Auto Backup / device transfer is enabled; the encrypted
  database may be transferred between your own devices, but is **excluded from
  cloud backup** and is never uploaded to cloud storage. The AI model file is
  always excluded from backup.

## Data Retention

- Transaction data stays on-device indefinitely until you delete or export it.
- You can export your data any time to CSV from **Settings → Data Management**.
- Uninstalling the app removes all data permanently.

## Third-Party Services

- **Sentry** (crash reporting) — see above; disabled by default and PII-stripped
  when enabled.
- **Google Play Asset Delivery** — delivers the bundled AI model.
- **Hugging Face / GitHub Releases** — optional fallback model download hosts.
- No advertising, marketing, or analytics SDKs are used.

## Children's Privacy

This app is not intended for children under 13, and we do not knowingly collect
data from children.

## Changes to This Policy

Updates are posted here with a new "Last Updated" date. Continued use after
changes constitutes acceptance of the revised policy.

## Contact

For privacy concerns or questions, please open an issue on our GitHub
repository or reach out via the GitHub repository page.

---

**Summary:** Your financial data stays on your device. Period.