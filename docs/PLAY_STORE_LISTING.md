# Play Store Listing Documentation

## App Description

### Short Description (80 chars max)
Track expenses automatically from UPI/bank notifications. 100% on-device, zero cloud.

### Full Description (4000 chars max)
Expense Tracker automatically categorizes your spending from UPI and bank notifications — completely on-device.

**KEY FEATURES:**
• Auto-extract transactions from Google Pay, PhonePe, Paytm, and bank SMS
• Smart categorization using on-device AI (Gemini Nano / MediaPipe)
• Interactive clarification for ambiguous transactions
• Spending insights with natural language queries
• Anomaly detection for unusual spending patterns
• 100% offline — your data never leaves your device

**HOW IT WORKS:**
1. Grant notification access to read payment app notifications
2. Grant SMS access to read bank transaction alerts
3. Transactions are automatically extracted and categorized
4. Review, edit, or confirm ambiguous transactions
5. View insights and query your spending in plain English

**PRIVACY-FIRST:**
• No server-side processing — all AI runs on your device
• No analytics, no tracking, no data collection
• Encrypted local database
• Open-source codebase

**SUPPORTED APPS:**
• Google Pay (UPI)
• PhonePe
• Paytm
• HDFC Bank, ICICI Bank, SBI, Axis Bank (SMS)
• Credit card apps (Amex, etc.)

**REQUIREMENTS:**
• Android 8.0+ (API 26+)
• For AI features: Android 12+ with 6GB+ RAM recommended
• One-time internet connection for AI model download (~50MB)

---

## Permission Justifications

### 1. Notification Listener Access

**Permission:** `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`

**Google Play Data Safety Declaration:**
- **Data type:** App info and performance (notification content)
- **Purpose:** App functionality
- **Is this required?** Yes
- **Is this for collecting user data?** No
- **Do you transmit this data?** No

**Justification:**
The app reads notifications from payment apps (Google Pay, PhonePe, Paytm) and bank apps to automatically extract transaction details (amount, merchant, category). This is the core functionality of the app.

**User-facing explanation:**
"Expense Tracker reads notifications from your payment apps to automatically track your spending. All processing happens on your device — no data is sent anywhere."

**How to revoke:**
Settings → Apps → Expense Tracker → Notifications → Disable notification access

---

### 2. SMS Read Access

**Permission:** `android.permission.READ_SMS`

**Google Play Data Safety Declaration:**
- **Data type:** Messages (SMS content)
- **Purpose:** App functionality
- **Is this required?** Yes
- **Is this for collecting user data?** No
- **Do you transmit this data?** No

**Justification:**
The app reads SMS messages from bank senders (e.g., HDFC, ICICI, SBI) to extract transaction details from bank alerts. This supplements notification-based tracking for users who receive SMS alerts instead of (or in addition to) app notifications.

**Restrictions:**
- Only reads SMS from whitelisted bank senders
- Does not access SMS from contacts, OTPs, or other apps
- All processing is on-device

**User-facing explanation:**
"Expense Tracker reads bank SMS alerts to track transactions. We only read SMS from your bank — not from contacts or other apps. All processing happens on your device."

**How to revoke:**
Settings → Apps → Expense Tracker → Permissions → SMS → Deny

---

### 3. Internet Access

**Permission:** `android.permission.INTERNET`

**Google Play Data Safety Declaration:**
- **Data type:** None (no user data transmitted)
- **Purpose:** App functionality (one-time model download)
- **Is this required?** Yes
- **Is this for collecting user data?** No
- **Do you transmit this data?** No

**Justification:**
The app requires internet access for one-time download of on-device AI models (~50MB) from Google's model hosting. After download, the app operates fully offline.

**What is downloaded:**
- Gemini Nano model (via Google AI Core)
- MediaPipe/Qwen2.5 model (fallback for devices without Gemini Nano)

**No ongoing network access** after initial model download.

**User-facing explanation:**
"Expense Tracker uses internet only once to download AI models (~50MB). After that, the app works fully offline. Your data is never transmitted."

---

## Data Safety Section

### Data Collection

| Data Type | Collected? | Shared? | Processed |
|-----------|------------|---------|-----------|
| Notification content | Yes (on-device) | No | On-device |
| SMS content | Yes (on-device) | No | On-device |
| Personal identifiers | No | No | N/A |
| Financial account numbers | No | No | N/A |
| Location | No | No | N/A |
| Contacts | No | No | N/A |

### Data Handling

- **Encryption:** All data stored in encrypted SQLite database
- **Deletion:** Users can delete all data via Settings → Clear Data, or by uninstalling the app
- **Retention:** Data is stored until manually deleted or app is uninstalled
- **Third-party sharing:** None

### Security Practices

- All data processing happens on-device
- No server-side data transmission
- No analytics or crash reporting
- Open-source codebase for transparency

---

## Content Rating

**IARC Rating:** Everyone

**Rationale:**
- No user-generated content
- No social features
- No in-app purchases
- No ads
- No external links

---

## Target Audience

**Age group:** 13+

**Rationale:**
- App handles financial data (transactions)
- Requires understanding of UPI/banking concepts
- Not designed for children

---

## Ads & In-App Purchases

- **Ads:** None
- **In-app purchases:** None
- **Subscription:** None

---

## App Access

**Account required:** No

**The app does not require sign-in or account creation.**

---

## COVID-19 Contact Tracing

**Not applicable.**

---

## Legal Compliance

**GDPR:** Compliant (no data collection, no server-side processing)

**CCPA:** Compliant (no data collection, no sale of personal information)

**COPPA:** Not applicable (app not directed at children under 13)

---

## Support

**Contact:** [GitHub Issues](https://github.com/yourusername/expense_tracker/issues)

**Website:** [GitHub Repository](https://github.com/yourusername/expense_tracker)
