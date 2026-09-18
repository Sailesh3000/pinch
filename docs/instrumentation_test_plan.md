# Instrumentation Test Validation Plan

Manual validation matrix for Pinch on real hardware. Run this on at least two
OEM devices before the Play Store closed-beta submission. Automated coverage is
too expensive to fully reproduce determinism (OEM battery optimizers, real
notification timing, 547 MB model transfer), so these checks are scripted
device tests.

## Validation Matrix

| Test Area | Device Target | What to Verify | Automated |
|-----------|---------------|----------------|-----------|
| Notification delivery timing | Samsung (OneUI), Xiaomi (MIUI), Stock Android | Notification arrives within 2s; listener not killed by battery optimization | `NotificationListenerServiceTest`* |
| AICore detection | Pixel 8+ (has AICore), Samsung S24 (may have) | `GeminiNanoExtractor.isAvailable()` returns the correct value | `ExtractorChainInstrumentedTest` |
| Model download | Real mobile connection (4G/WiFi) | ~547 MB downloads with retry/resume; progress updates in Settings UI | `ModelDownloaderResumeTest` (JVM) + manual |
| Model download interruption | Kill app mid-download, toggle airplane mode | Download resumes from partial `.tmp` file via HTTP `Range` | `ModelDownloaderResumeTest` (JVM) |
| Background persistence | Samsung (aggressive Doze), Xiaomi (AutoStart) | Listener survives 30+ minutes in background | Manual |

\* `NotificationListenerServiceTest` is an instrumentation test that asserts the
manifest contract (service declared, exported false, correct permission, loadable
class). Actual notification arrival timing remains a manual check because the OS
governs when `onNotificationPosted` fires and cannot be scripted deterministically.

## Manual Script: Model Download

1. Fresh install the debug build.
2. From Settings, start the AI model download (do NOT grant battery optimization exemption yet).
3. While downloading, kill the app. Reopen and watch Settings: the download should resume from where it stopped (progress jumps, not restarts at 0%).
4. Toggle airplane mode mid-download; network loss should surface the error card, and re-connect + Retry should resume from the partial file.
5. Verify the final `.litertlm` file passes the ~50 MB sanity check and the AI Engine card flips to "model installed locally".

## Manual Script: Background Persistence

1. Enable notification access, grant Pinch AutoStart/background permission where offered (Xiaomi), and disable battery optimization for Pinch (Samsung).
2. Send a test transaction notification (an actual UPI payment works best).
3. Wait 30+ minutes with the screen off.
4. Send a second notification. Verify both were captured in the transaction list, proving the listener survived doze.

## Demo Video Storyboard (Play Console Notification Access Recording)

1. **Onboarding disclosure** — user sees the in-app explanation of why notification access is needed (only financial app notifications; everything on-device).
2. **Permission grant** — user navigates to Settings -> Notifications -> Notification access and enables Pinch.
3. **Notification arrival** — trigger a real UPI payment (PhonePe/GPay) and let the notification post.
4. **Categorized transaction** — open Pinch, show the transaction with merchant, amount, and category populated, and note it all stays on-device.

## Commands

```bash
# JVM resume/fallback tests (no device required)
./gradlew testDebugUnitTest --tests "com.expensetracker.extraction.ModelDownloaderResumeTest"

# Instrumentation tests on a connected device
./gradlew connectedDebugAndroidTest
```