package com.expensetracker.ingestion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Spec §12.1 — PreFilterEngineTest: verify positive and negative keyword
 * matching across representative notification strings (FR-INGEST-03).
 */
class PreFilterEngineTest {

    private lateinit var whitelist: PackageWhitelist
    private lateinit var preFilter: PreFilterEngine

    @Before
    fun setUp() {
        whitelist = PackageWhitelist()
        whitelist.refresh(
            listOf(
                com.expensetracker.core.database.entity.MonitoredPackageEntity(
                    packageName = "com.phonepe.app",
                    appLabel = "PhonePe",
                ),
                com.expensetracker.core.database.entity.MonitoredPackageEntity(
                    packageName = "com.google.android.apps.nbu.paisa.user",
                    appLabel = "Google Pay",
                ),
                com.expensetracker.core.database.entity.MonitoredPackageEntity(
                    packageName = "com.google.android.apps.messaging",
                    appLabel = "Messages",
                ),
                com.expensetracker.core.database.entity.MonitoredPackageEntity(
                    packageName = "com.snapwork.hdfc",
                    appLabel = "HDFC",
                ),
                com.expensetracker.core.database.entity.MonitoredPackageEntity(
                    packageName = "com.revolut.revolut",
                    appLabel = "Revolut",
                ),
            )
        )
        preFilter = PreFilterEngine(whitelist)
    }

    @Test
    fun `debit from bank app is a candidate`() {
        assertTrue(
            preFilter.isFinancialCandidate(
                "com.snapwork.hdfc",
                "Alert",
                "Rs 850.00 debited from A/c *1234 at Swiggy. UPI Ref 123456789012",
            )
        )
    }

    @Test
    fun `credit via upi app is a candidate`() {
        assertTrue(
            preFilter.isFinancialCandidate(
                "com.phonepe.app",
                "Money received",
                "You received Rs 15,000 from Ramesh Kumar. UPI Ref 9876543210.",
            )
        )
    }

    @Test
    fun `sms notification from messages app is a candidate`() {
        assertTrue(
            preFilter.isFinancialCandidate(
                "com.google.android.apps.messaging",
                "HDFCBK",
                "INR 1200.00 spent at Big Bazaar. Avl Bal 54000.",
            )
        )
    }

    @Test
    fun `non-whitelisted package is dropped even with money words`() {
        assertFalse(
            preFilter.isFinancialCandidate(
                "com.whatsapp",
                "Friend",
                "I spent 500 on lunch yesterday",
            )
        )
    }

    @Test
    fun `otp notification is dropped immediately`() {
        assertFalse(
            preFilter.isFinancialCandidate(
                "com.snapwork.hdfc",
                "HDFCBK",
                "OTP 7890 for transaction of Rs 8500. Do not share.",
            )
        )
    }

    @Test
    fun `promotional offer is dropped`() {
        assertFalse(
            preFilter.isFinancialCandidate(
                "com.phonepe.app",
                "Exclusive offer",
                "Pre-approved loan of Rs 5,00,000 just for you. Exclusive offer ends soon.",
            )
        )
    }

    @Test
    fun `security code alert is dropped`() {
        assertFalse(
            preFilter.isFinancialCandidate(
                "com.google.android.apps.messaging",
                "Security",
                "Your verification code is 481516. Security code for login.",
            )
        )
    }

    @Test
    fun `purchase with dollar symbol is a candidate`() {
        assertTrue(
            preFilter.isFinancialCandidate(
                "com.revolut.revolut",
                "Purchase",
                "$45.00 spent at Starbucks on card ending 9012.",
            )
        )
    }

    @Test
    fun `refund is a candidate`() {
        assertTrue(
            preFilter.isFinancialCandidate(
                "com.google.android.apps.nbu.paisa.user",
                "Refund",
                "Refund of Rs 250 received from Flipkart.",
            )
        )
    }

    @Test
    fun `non-financial reminder is dropped`() {
        assertFalse(
            preFilter.isFinancialCandidate(
                "com.google.android.apps.messaging",
                "Meeting reminder",
                "Reminder: Standup at 10am tomorrow.",
            )
        )
    }

    @Test
    fun `cashback credit is dropped`() {
        assertFalse(
            preFilter.isFinancialCandidate(
                "com.phonepe.app",
                "Cashback credited",
                "You've earned ₹50 cashback on your last order.",
            )
        )
    }

    @Test
    fun `reward won notification is dropped`() {
        assertFalse(
            preFilter.isFinancialCandidate(
                "com.google.android.apps.nbu.paisa.user",
                "You're a winner!",
                "Congratulations! You won a reward of Rs 200.",
            )
        )
    }

    @Test
    fun `sale and cashback promo is dropped`() {
        assertFalse(
            preFilter.isFinancialCandidate(
                "net.one97.paytm",
                "Big sale",
                "Flat 20% off on your next Swiggy order, plus instant cashback.",
            )
        )
    }

    @Test
    fun `ad sms quoting a price with no transaction verb is dropped`() {
        assertFalse(
            preFilter.isFinancialCandidate(
                "com.google.android.apps.messaging",
                "AD-650025-P",
                "Watch India vs Sri Lanka on Sony LIV. Get Box Office Pack at Rs. 200. Recharge now.",
            )
        )
    }

    @Test
    fun `p2p receive notification with amount between sent and to is a candidate`() {
        assertTrue(
            preFilter.isFinancialCandidate(
                "com.phonepe.app",
                "Varun",
                "sent ₹10 to you.",
            )
        )
    }

    @Test
    fun `dr abbreviation bank sms is a candidate`() {
        assertTrue(
            preFilter.isFinancialCandidate(
                "com.google.android.apps.messaging",
                "AX-CANBNK-S",
                "Dear Customer, Acct XXX331 Dr. INR 10.00 on 26/08/26 to KONDAPURAM V; UPI: 669992992861; Bal INR 1,261.97.Not you?SMS BLOCKUPI to 9901771222-CanaraBank",
            )
        )
    }

    @Test
    fun `investment ad quoting bare currency amounts is dropped`() {
        assertFalse(
            preFilter.isFinancialCandidate(
                "com.phonepe.app",
                "Daily RD",
                "Turn ₹100/day into ₹37,611*! Set up Daily RD to maximize your savings.",
            )
        )
    }
}
