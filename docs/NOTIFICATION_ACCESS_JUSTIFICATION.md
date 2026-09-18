# Notification Access Justification

**App**: Pinch — Expense Tracker
**Package**: `com.expensetracker`
**Feature**: `TransactionNotificationListenerService` (NotificationListenerService)

## Why the permission is required

Pinch works by passively reading SMS-style but app-native transaction
notifications pushed by the user's banking, UPI, and wallet apps (PhonePe,
GPay, Paytm, Apple Pay, Monzo, Standard Chartered, etc.). Android exposes no
other user-permission-based API that lets a third-party app observe these
incoming notifications, so the system's official
`BIND_NOTIFICATION_LISTENER_SERVICE` permission is the *only* mechanism that
gives Pinch access to the data users ask it to track. (The alternative,
SMS permission, is read-only for the deprecated SMS table, is restricted, and
does not surface push notifications at all.)

## Exactly what is accessed

- The **text body** of incoming transaction notifications emitted within
  Pinch's monitored-app whitelist (in-app, user-visible list).
- The **package name** of the notifying app (already public app metadata).

## What is NOT accessed / collected

Pinch does **not** log, count, or transmit:

- Any information about apps outside the user's monitored whitelist.
- Notification *titles*, or metadata of other users.
- Content of non-transaction notifications from monitored apps.
- SMS history, phonebook, location, photos, or any other private data.

## Data handling

- All parsing runs **on-device**. Parsed `(amount, merchant, category,
  timestamp)` fields are written only to the app's local, SQLCipher-encrypted
  Room database.
- Nothing extracted from notifications is uploaded anywhere, and the app uses
  no analytics (crash reports via Sentry are enabled only when a DSN is
  configured, are opt-opted-out for PII, and a scrubber strips any
  financial-looking tokens before events leave the device).
- Unread notification bodies are discarded: Pinch parses on receipt and does
  not retain raw message text.

## Failure to grant = graceful degradation

If the user denies this permission, the app still runs fully: manual entry and
the regex/AI extraction flows remain available, permission status is shown in
Settings, and a single tap deep-links to the system screen for re-enabling.

## Play Console policy alignment

This disclosure mirrors the Google Play "Notification Listeners Permission"
policy requirements: it explains (1) why the permission is used, (2) the data
it accesses, (3) the data published/transmitted (none), and (4) how to revoke
it (Settings > Apps > Pinch > Permissions / system notification-access list).