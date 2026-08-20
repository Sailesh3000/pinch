package com.expensetracker.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §12.1 #3 Automated accuracy & benchmark harness — runs the deterministic
 * extractor over the full 500+ notification corpus and enforces the spec
 * targets. The deterministic regex engine is the always-available tier and is
 * the same code path a non-AICore device uses, so this guards the on-device
 * pipeline end-to-end on the JVM.
 */
class BenchmarkSuiteTest {

    private val harness = BenchmarkHarness()
    private val metrics = harness.run()

    @Test
    fun `corpus has 500+ realistic notifications`() {
        assertTrue("Corpus must have 500+ samples, got ${BenchmarkCorpus.size}", BenchmarkCorpus.size >= 500)
    }

    @Test
    fun `covers all target banks and apps`() {
        val pkgs = BenchmarkCorpus.samples.map { it.packageName }.toSet()
        assertTrue(pkgs.contains("com.google.android.apps.nbu.paisa.user"))
        assertTrue(pkgs.contains("com.phonepe.app"))
        assertTrue(pkgs.contains("net.one97.paytm"))
        assertTrue(pkgs.contains("com.hdfc.bank"))
        assertTrue(pkgs.contains("com.icici.bank"))
        assertTrue(pkgs.contains("com.americanexpress.android.acctsvc.us"))
    }

    @Test
    fun `exact amount match rate is 100 percent`() {
        assertEquals(
            "Amount literals must be copied exactly (zero-arithmetic rule)",
            100.0,
            metrics.amountMatchRate,
            0.001,
        )
    }

    @Test
    fun `merchant extraction accuracy exceeds 98 percent`() {
        assertTrue(
            "Merchant accuracy ${metrics.merchantAccuracy}% below 98%",
            metrics.merchantAccuracy > 98.0,
        )
    }

    @Test
    fun `category classification exceeds 95 percent`() {
        assertTrue(
            "Category accuracy ${metrics.categoryAccuracy}% below 95%",
            metrics.categoryAccuracy > 95.0,
        )
    }

    @Test
    fun `confidence calibration exceeds 99 percent`() {
        assertTrue(
            "Confidence calibration ${metrics.confidenceCalibration}% below 99%",
            metrics.confidenceCalibration > 99.0,
        )
    }

    @Test
    fun `arithmetic consistency is 100 percent exact`() {
        assertEquals(
            "Extracted amounts must reconcile exactly to SQL totals",
            100.0,
            metrics.arithmeticConsistencyRate,
            0.001,
        )
    }

    @Test
    fun `non-financial notifications are dropped`() {
        val nonFin = BenchmarkCorpus.samples.count { it.expectedType == "NON_FINANCIAL" }
        assertEquals(
            "All $nonFin non-financial samples must be dropped, dropped=${metrics.droppedNonFinancial}",
            nonFin,
            metrics.droppedNonFinancial,
        )
    }
}