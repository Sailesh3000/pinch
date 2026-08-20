package com.expensetracker.worker

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClarificationAutoDecayTest {

    private val fakeTransactionDao = FakeTransactionDaoForDecay()
    private val fakeTemplateCacheDao = com.expensetracker.extraction.FakeTemplateCacheDao()

    private fun createWorker() = FakeDecayWorker(fakeTransactionDao, fakeTemplateCacheDao)

    @Test
    fun `resolves transactions older than 48 hours`() = runTest {
        val now = System.currentTimeMillis()
        fakeTransactionDao.addPending(id = 1, timestamp = now - ClarificationAutoDecayWorker.FORTY_EIGHT_HOURS_MS - 1000)
        fakeTransactionDao.addPending(id = 2, timestamp = now - ClarificationAutoDecayWorker.FORTY_EIGHT_HOURS_MS + 1000)

        val resolved = createWorker().resolveExpired(now)

        assertEquals(1, resolved)
        assertEquals(1, fakeTransactionDao.pendingIds.size)
        assertTrue(fakeTransactionDao.pendingIds.contains(2))
    }

    @Test
    fun `resolves nothing when all transactions are recent`() = runTest {
        val now = System.currentTimeMillis()
        fakeTransactionDao.addPending(id = 1, timestamp = now - 1000)
        fakeTransactionDao.addPending(id = 2, timestamp = now - 2000)

        val resolved = createWorker().resolveExpired(now)

        assertEquals(0, resolved)
        assertEquals(2, fakeTransactionDao.pendingIds.size)
    }

    @Test
    fun `resolves all when all transactions are expired`() = runTest {
        val now = System.currentTimeMillis()
        fakeTransactionDao.addPending(id = 1, timestamp = now - ClarificationAutoDecayWorker.FORTY_EIGHT_HOURS_MS - 10000)
        fakeTransactionDao.addPending(id = 2, timestamp = now - ClarificationAutoDecayWorker.FORTY_EIGHT_HOURS_MS - 5000)
        fakeTransactionDao.addPending(id = 3, timestamp = now - ClarificationAutoDecayWorker.FORTY_EIGHT_HOURS_MS - 1000)

        val resolved = createWorker().resolveExpired(now)

        assertEquals(3, resolved)
        assertTrue(fakeTransactionDao.pendingIds.isEmpty())
    }

    @Test
    fun `handles empty pending queue`() = runTest {
        val resolved = createWorker().resolveExpired(System.currentTimeMillis())
        assertEquals(0, resolved)
    }

    @Test
    fun `48-hour boundary is exactly correct`() = runTest {
        val now = System.currentTimeMillis()
        fakeTransactionDao.addPending(id = 1, timestamp = now - ClarificationAutoDecayWorker.FORTY_EIGHT_HOURS_MS)
        val resolved = createWorker().resolveExpired(now)
        assertEquals(0, resolved) // at boundary: timestamp < cutoff is false
    }
}
