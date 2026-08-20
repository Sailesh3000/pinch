package com.expensetracker.extraction

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for [DeterministicRegexExtractor] — verifies extraction
 * behavior on a real Android device/emulator (not Robolectric).
 */
@RunWith(AndroidJUnit4::class)
class DeterministicRegexExtractorInstrumentedTest {

    private val extractor = DeterministicRegexExtractor()

    @Test
    fun extract_gpayDebit_returnsCorrectAmountAndMerchant() = runBlocking {
        val result = extractor.extract(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Money debited from your account",
            body = "₹79.10 paid to SWIGGY via UPI. UPI Ref: 402931827",
            timestampEpoch = 1_700_000_000_000L,
        )
        assertNotNull(result)
        assertEquals(79.10, result!!.amount, 0.001)
        assertEquals("SWIGGY", result.merchantOrPayee)
        assertEquals("Food & Dining", result.category)
        assertEquals("DEBIT", result.txnType)
    }

    @Test
    fun extract_phonePeDebit_returnsCorrectAmountAndMerchant() = runBlocking {
        val result = extractor.extract(
            packageName = "com.phonepe.app",
            title = "Payment successful",
            body = "₹450.00 paid to Zomato via PhonePe. Txn ID: 70001100",
            timestampEpoch = 1_700_000_000_000L,
        )
        assertNotNull(result)
        assertEquals(450.00, result!!.amount, 0.001)
        assertEquals("Zomato", result.merchantOrPayee)
        assertEquals("Food & Dining", result.category)
    }

    @Test
    fun extract_bankSmsDebit_returnsCorrectAmountAndMerchant() = runBlocking {
        val result = extractor.extract(
            packageName = "com.hdfc.bank",
            title = "HDFC Bank debit alert",
            body = "₹1234.56 debited from a/c **8500 at AMAZON on 12-AUG. Available bal: ₹18500.",
            timestampEpoch = 1_700_000_000_000L,
        )
        assertNotNull(result)
        assertEquals(1234.56, result!!.amount, 0.001)
        assertEquals("AMAZON", result.merchantOrPayee)
        assertEquals("Shopping", result.category)
    }

    @Test
    fun extract_amexPurchase_returnsCorrectAmountAndMerchant() = runBlocking {
        val result = extractor.extract(
            packageName = "com.americanexpress.android.acctsvc.us",
            title = "Amex Purchase",
            body = "INR 256.79 at AMAZON.IN. Card ending 1004 on 12-AUG.",
            timestampEpoch = 1_700_000_000_000L,
        )
        assertNotNull(result)
        assertEquals(256.79, result!!.amount, 0.001)
        assertEquals("AMAZON.IN", result.merchantOrPayee)
    }

    @Test
    fun extract_otpNotification_returnsNull() = runBlocking {
        val result = extractor.extract(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "OTP for UPI registration",
            body = "Your OTP is 381920. Do not share it with anyone.",
            timestampEpoch = 1_700_000_000_000L,
        )
        assertNull("OTP notification must be dropped (non-financial)", result)
    }

    @Test
    fun extract_loginAlert_returnsNull() = runBlocking {
        val result = extractor.extract(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "UPI Login Alert",
            body = "New device login detected. If this was you, no action needed.",
            timestampEpoch = 1_700_000_000_000L,
        )
        assertNull("Login alert must be dropped (non-financial)", result)
    }

    @Test
    fun extract_offerNotification_returnsNull() = runBlocking {
        val result = extractor.extract(
            packageName = "com.phonepe.app",
            title = "Flat 50% OFF offer",
            body = "Limited period offer on your next food order. Check the app!",
            timestampEpoch = 1_700_000_000_000L,
        )
        assertNull("Offer notification must be dropped (non-financial)", result)
    }

    @Test
    fun extract_personalTransfer_returnsLowConfidence() = runBlocking {
        val result = extractor.extract(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Money debited",
            body = "₹500.00 paid to Ramesh Kumar via UPI. Ref 701112233",
            timestampEpoch = 1_700_000_000_000L,
        )
        assertNotNull(result)
        assertEquals(500.00, result!!.amount, 0.001)
        assertEquals("Ramesh Kumar", result.merchantOrPayee)
        assertEquals("Uncategorized", result.category)
        assertEquals(
            "Personal transfer must have low confidence for clarification",
            0.45f,
            result.confidenceScore,
            0.001f,
        )
    }

    @Test
    fun extract_largeAmount_parsesCorrectly() = runBlocking {
        val result = extractor.extract(
            packageName = "com.phonepe.app",
            title = "Payment successful",
            body = "₹3840.75 paid to Swiggy via UPI. UPI Ref: 402931827",
            timestampEpoch = 1_700_000_000_000L,
        )
        assertNotNull(result)
        assertEquals(
            "4+ digit amounts without commas must parse correctly",
            3840.75,
            result!!.amount,
            0.001,
        )
    }

    @Test
    fun extract_commaFormattedAmount_parsesCorrectly() = runBlocking {
        val result = extractor.extract(
            packageName = "com.hdfc.bank",
            title = "HDFC Bank debit alert",
            body = "₹1,234.56 debited from a/c **8500 at AMAZON on 12-AUG.",
            timestampEpoch = 1_700_000_000_000L,
        )
        assertNotNull(result)
        assertEquals(1234.56, result!!.amount, 0.001)
    }
}
