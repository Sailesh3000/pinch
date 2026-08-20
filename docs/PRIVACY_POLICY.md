# Privacy Policy

**Last Updated:** 2026-08-20

## Overview

Expense Tracker is a 100% on-device expense tracking application. Your financial data never leaves your device. This privacy policy explains what data we access, how it is used, and what is never collected.

## Data Collection & Usage

### What We Access

| Data Type | Purpose | Storage |
|-----------|---------|---------|
| **Notification content** from payment apps (GPay, PhonePe, Paytm, bank apps) | Extract transaction details (amount, merchant, category) | On-device only |
| **SMS content** from bank SMS senders | Extract transaction details from bank alerts | On-device only |

### What We NEVER Collect

- **No personal identifiers** (name, email, phone number, device ID)
- **No financial account numbers** (we extract merchant names, not account details)
- **No location data**
- **No contacts or call logs**
- **No analytics or crash reporting**
- **No server-side data transmission**

## Permissions Explained

### Notification Listener Access (`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`)

**Why we need it:** To read transaction notifications from payment apps (Google Pay, PhonePe, Paytm) and bank apps in real-time.

**What we do with it:**
- Parse notification text to extract amount, merchant, and category
- Store extracted data locally in an encrypted SQLite database
- **Never transmit notification content to any server**

**How to revoke:** Settings → Apps → Expense Tracker → Notifications → Disable notification access

### SMS Read Access (`android.permission.READ_SMS`)

**Why we need it:** To read bank transaction SMS alerts (e.g., "Rs. 500 debited from A/c *1234 at SWIGGY").

**What we do with it:**
- Parse SMS text from whitelisted bank senders
- Extract transaction details using on-device regex patterns
- **Never transmit SMS content to any server**
- **Never access SMS from non-bank senders**

**How to revoke:** Settings → Apps → Expense Tracker → Permissions → SMS → Deny

### Internet Access (`android.permission.INTERNET`)

**Why we need it:** One-time download of on-device AI models (Gemini Nano, MediaPipe) from Google's model hosting.

**What we do with it:**
- Download AI models (~50MB) on first launch
- **No ongoing network access after model download**
- **No data transmission after initial setup**

## On-Device AI Processing

All AI processing happens locally on your device:

1. **Gemini Nano** (via Google AI Core) — Runs on-device for transaction categorization
2. **MediaPipe** (Qwen2.5-0.5B model) — Falls back to on-device inference when Gemini Nano is unavailable
3. **Deterministic Regex** — Final fallback that works without any AI model

**No data is sent to cloud AI services.** All inference happens locally.

## Data Storage

- **Database:** Encrypted SQLite database stored in app's private directory (`/data/data/com.expensetracker/databases/`)
- **AI Models:** Stored in app's private cache directory
- **Backup:** Data is NOT included in Android Auto Backup (excluded via `android:data_extraction_rules`)

## Data Retention

- Transaction data is stored indefinitely until you manually delete it or uninstall the app
- Uninstalling the app removes all data permanently

## Third-Party Services

This app does **not** use:
- Google Analytics
- Firebase
- Crashlytics
- Any third-party SDKs that collect data

The only external resource is the one-time AI model download from Google's model hosting.

## Children's Privacy

This app is not intended for children under 13. We do not knowingly collect data from children.

## Changes to This Policy

We will update this privacy policy if our practices change. Updates will be posted here with a new "Last Updated" date.

## Contact

For privacy concerns or questions, please open an issue on our GitHub repository.

---

**Summary:** Your financial data stays on your device. Period.
