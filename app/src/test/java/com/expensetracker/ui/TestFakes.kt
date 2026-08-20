package com.expensetracker.ui

import com.expensetracker.core.database.dao.CategoryDao
import com.expensetracker.core.database.dao.CategorySpend
import com.expensetracker.core.database.dao.ClarificationHistoryDao
import com.expensetracker.core.database.dao.MonitoredPackageDao
import com.expensetracker.core.database.dao.RecurringMerchantRow
import com.expensetracker.core.database.dao.TemplateCacheDao
import com.expensetracker.core.database.dao.TransactionDao
import com.expensetracker.core.database.dao.TransactionDao.ConfidenceStats
import com.expensetracker.core.database.dao.TransactionDao.DebitTransaction
import com.expensetracker.core.database.dao.TransactionDao.MerchantFrequencyRow
import com.expensetracker.core.database.dao.TransactionDao.MonthlySpend
import com.expensetracker.core.database.dao.TransactionDao.TopMerchantRow
import com.expensetracker.core.database.dao.TransactionWithCategory
import com.expensetracker.core.database.entity.CategoryEntity
import com.expensetracker.core.database.entity.ClarificationHistoryEntity
import com.expensetracker.core.database.entity.MonitoredPackageEntity
import com.expensetracker.core.database.entity.TemplateCacheEntity
import com.expensetracker.core.database.entity.TransactionEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Deterministic sample data for the Roborazzi screenshot suite.
 * All timestamps are anchored relative to "now" so the screens render a
 * realistic current-month feed no matter when the tests run.
 */
object SampleData {

    fun daysAgo(days: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, -days)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    val categories = listOf(
        CategoryEntity(id = 1, name = "Food & Dining", iconKey = "FOOD", colorHex = "#E8590C", usageCount = 18),
        CategoryEntity(id = 2, name = "Groceries", iconKey = "GROCERIES", colorHex = "#2B8A3E", usageCount = 24),
        CategoryEntity(id = 3, name = "Transportation", iconKey = "TRANSPORTATION", colorHex = "#0CA678", usageCount = 15),
        CategoryEntity(id = 4, name = "Shopping", iconKey = "SHOPPING", colorHex = "#E64980", usageCount = 12),
        CategoryEntity(id = 5, name = "Bills & Utilities", iconKey = "BILLS", colorHex = "#F08C00", usageCount = 10),
        CategoryEntity(id = 6, name = "Entertainment", iconKey = "ENTERTAINMENT", colorHex = "#7048E8", usageCount = 8),
        CategoryEntity(id = 7, name = "Health & Medical", iconKey = "HEALTH", colorHex = "#C2255C", usageCount = 4),
        CategoryEntity(id = 8, name = "Salary/Income", iconKey = "INCOME", colorHex = "#2F9E44", usageCount = 2),
    )

    private fun txn(
        id: Long,
        merchant: String,
        amount: Double,
        categoryId: Long,
        timestamp: Long,
        confidence: Float,
        tier: String,
        type: String = "DEBIT",
        needsClarification: Boolean = false,
        isClarified: Boolean = true,
        sourcePackage: String = "com.phonepe.app",
    ) = TransactionEntity(
        id = id,
        amount = amount,
        currency = "INR",
        txnType = type,
        merchantName = merchant,
        cleanPayee = merchant,
        categoryId = categoryId,
        timestamp = timestamp,
        sourcePackage = sourcePackage,
        sourceType = "APP",
        rawNotificationText = "Payment of Rs. $amount to $merchant",
        confidenceScore = confidence,
        confidenceTier = tier,
        needsClarification = needsClarification,
        isClarified = isClarified,
        dedupHash = null,
        mergedFromDualSource = false,
        accountReference = null,
        createdAt = timestamp,
    )

    val transactions: List<TransactionWithCategory> = buildList {
        fun add(t: TransactionEntity) {
            val cat = categories.first { it.id == t.categoryId }
            add(TransactionWithCategory(t, cat.name, cat.iconKey, cat.colorHex))
        }

        // --- This month ---
        add(txn(1, "Swiggy", 486.50, 1, daysAgo(0, 13), 0.92f, "HIGH"))
        add(txn(2, "HDFC Salary", 85000.00, 8, daysAgo(0, 9), 1.0f, "HIGH", type = "CREDIT", sourcePackage = "com.snapwork.hdfc"))
        add(txn(3, "BigBasket", 1240.75, 2, daysAgo(1, 19), 0.88f, "HIGH", sourcePackage = "com.google.android.apps.nbu.paisa.user"))
        add(txn(4, "Paytm", 340.25, 1, daysAgo(1, 11), 0.45f, "LOW", needsClarification = true, isClarified = false, sourcePackage = "net.one97.paytm"))
        add(txn(5, "Amazon India", 3199.00, 4, daysAgo(2, 16), 0.90f, "HIGH", sourcePackage = "com.sbi.SBIFreedomPlus"))
        add(txn(6, "Apollo Pharmacy", 560.30, 7, daysAgo(2, 10), 0.72f, "MEDIUM"))
        add(txn(7, "CRED", 1999.00, 4, daysAgo(4, 14), 0.42f, "LOW", needsClarification = true, isClarified = false, sourcePackage = "com.dreamplug.androidapp"))
        add(txn(8, "Uber", 245.60, 3, daysAgo(5, 18), 0.85f, "HIGH", sourcePackage = "com.google.android.apps.nbu.paisa.user"))
        add(txn(9, "Netflix", 149.00, 6, daysAgo(7, 8), 0.99f, "HIGH", sourcePackage = "com.google.android.apps.nbu.paisa.user"))
        add(txn(10, "BPCL Petrol", 1000.00, 3, daysAgo(7, 17), 0.80f, "HIGH", sourcePackage = "com.icici.mobile"))
        add(txn(11, "Spotify", 119.00, 6, daysAgo(16, 9), 0.99f, "HIGH", sourcePackage = "com.icici.mobile"))

        // --- Last month ---
        add(txn(12, "Netflix", 149.00, 6, daysAgo(38), 0.99f, "HIGH"))
        add(txn(13, "Spotify", 119.00, 6, daysAgo(47), 0.99f, "HIGH"))
        add(txn(14, "Amazon India", 1599.00, 4, daysAgo(42), 0.90f, "HIGH", sourcePackage = "com.sbi.SBIFreedomPlus"))
        add(txn(15, "BigBasket", 980.40, 2, daysAgo(45), 0.88f, "HIGH"))
        add(txn(16, "BPCL Petrol", 850.00, 3, daysAgo(48), 0.80f, "HIGH"))
        add(txn(17, "Swiggy", 720.25, 1, daysAgo(35), 0.92f, "HIGH"))
        add(txn(18, "SBI Credit Card", 2100.00, 4, daysAgo(40), 0.86f, "HIGH", sourcePackage = "com.sbi.SBIFreedomPlus"))

        // --- Two months ago (subscription history) ---
        add(txn(19, "BigBasket", 1350.00, 2, daysAgo(70), 0.88f, "HIGH"))
        add(txn(20, "Amazon India", 2299.00, 4, daysAgo(65), 0.90f, "HIGH"))
        add(txn(21, "BPCL Petrol", 950.00, 3, daysAgo(60), 0.80f, "HIGH"))

        // --- 3-5 months ago (6-month trend bars) ---
        add(txn(22, "Flipkart", 1850.00, 4, daysAgo(91), 0.85f, "HIGH"))
        add(txn(23, "Reliance Digital", 2400.00, 4, daysAgo(123), 0.85f, "HIGH"))
        add(txn(24, "Myntra", 1600.00, 4, daysAgo(151), 0.85f, "HIGH"))
    }

    val monitoredPackages = listOf(
        MonitoredPackageEntity(id = 1, packageName = "com.sbi.SBIFreedomPlus", appLabel = "SBI YONO", isEnabled = true),
        MonitoredPackageEntity(id = 2, packageName = "com.google.android.apps.nbu.paisa.user", appLabel = "Google Pay", isEnabled = true),
        MonitoredPackageEntity(id = 3, packageName = "com.phonepe.app", appLabel = "PhonePe", isEnabled = true),
        MonitoredPackageEntity(id = 4, packageName = "com.snapwork.hdfc", appLabel = "HDFC MobileBanking", isEnabled = true),
        MonitoredPackageEntity(id = 5, packageName = "com.icici.mobile", appLabel = "ICICI iMobile", isEnabled = false),
        MonitoredPackageEntity(id = 6, packageName = "com.dreamplug.androidapp", appLabel = "CRED", isEnabled = true),
        MonitoredPackageEntity(id = 7, packageName = "net.one97.paytm", appLabel = "Paytm", isEnabled = true),
    )
}

/** In-memory TransactionDao backed by [SampleData.transactions]. */
class FakeTransactionDao(
    private val rows: MutableList<TransactionWithCategory> = SampleData.transactions.toMutableList(),
) : TransactionDao {

    private val sorted get() = rows.sortedByDescending { it.transaction.timestamp }

    private fun inRange(start: Long, end: Long) =
        rows.filter { it.transaction.timestamp in start..end }

    private fun inRangeDebits(start: Long, end: Long) =
        inRange(start, end).filter { it.transaction.txnType == "DEBIT" }

    override fun observeAll(): Flow<List<TransactionWithCategory>> = flowOf(sorted.toList())

    override fun observeFiltered(search: String?, categoryId: Long?): Flow<List<TransactionWithCategory>> = flowOf(
        sorted.filter { row ->
            val matchesSearch = search.isNullOrBlank() ||
                row.transaction.merchantName.contains(search, ignoreCase = true) ||
                (row.transaction.cleanPayee ?: "").contains(search, ignoreCase = true)
            val matchesCategory = categoryId == null || row.transaction.categoryId == categoryId
            matchesSearch && matchesCategory
        }.toList(),
    )

    override fun observeClarificationCount(): Flow<Int> =
        flowOf(rows.count { it.transaction.needsClarification })

    override fun observeClarificationQueue(): Flow<List<TransactionWithCategory>> =
        flowOf(rows.filter { it.transaction.needsClarification }.sortedBy { it.transaction.timestamp }.toList())

    override suspend fun getById(id: Long): TransactionEntity? =
        rows.firstOrNull { it.transaction.id == id }?.transaction

    override suspend fun insert(entity: TransactionEntity): Long {
        rows.add(TransactionWithCategory(entity, null, null, null))
        return entity.id
    }

    override suspend fun update(entity: TransactionEntity) {
        val idx = rows.indexOfFirst { it.transaction.id == entity.id }
        if (idx >= 0) rows[idx] = rows[idx].copy(transaction = entity)
    }

    override suspend fun deleteById(id: Long) {
        rows.removeAll { it.transaction.id == id }
    }

    override suspend fun deleteAll() {
        rows.clear()
    }

    override suspend fun sumDebitsBetween(start: Long, end: Long): Double =
        inRangeDebits(start, end).sumOf { it.transaction.amount }

    override suspend fun sumCreditsBetween(start: Long, end: Long): Double =
        inRange(start, end).filter { it.transaction.txnType == "CREDIT" }.sumOf { it.transaction.amount }

    override suspend fun countBetween(start: Long, end: Long): Int = inRange(start, end).size

    override suspend fun categoryBreakdown(start: Long, end: Long): List<CategorySpend> =
        inRangeDebits(start, end)
            .groupBy { it.categoryName ?: "Uncategorized" }
            .map { (name, list) ->
                val cat = SampleData.categories.firstOrNull { it.name == name }
                CategorySpend(
                    categoryName = name,
                    iconKey = cat?.iconKey,
                    colorHex = cat?.colorHex,
                    totalAmount = list.sumOf { it.transaction.amount },
                    transactionCount = list.size,
                )
            }
            .sortedByDescending { it.totalAmount }

    override suspend fun updateClarificationState(id: Long, needs: Boolean) {
        val row = rows.first { it.transaction.id == id }
        update(row.transaction.copy(needsClarification = needs))
    }

    override suspend fun markMerged(dedupHash: String) {
        // no-op
    }

    override suspend fun autoResolveExpired(cutoffMillis: Long): Int {
        val expired = rows.filter { it.transaction.needsClarification && it.transaction.timestamp < cutoffMillis }
        expired.forEach {
            update(
                it.transaction.copy(
                    needsClarification = false,
                    isClarified = true,
                    confidenceTier = "AUTO_RESOLVED_AFTER_TIMEOUT",
                ),
            )
        }
        return expired.size
    }

    override suspend fun topCategoriesForMerchant(merchant: String, limit: Int): List<CategoryEntity> =
        SampleData.categories.take(limit)

    override suspend fun topMerchants(start: Long, end: Long, limit: Int): List<TopMerchantRow> =
        inRangeDebits(start, end)
            .groupBy { it.transaction.merchantName }
            .map { (name, list) ->
                TopMerchantRow(name, list.sumOf { it.transaction.amount }, list.size)
            }
            .sortedByDescending { it.totalAmount }
            .take(limit)

    override suspend fun confidenceStats(start: Long, end: Long): ConfidenceStats {
        val list = inRange(start, end)
        return ConfidenceStats(
            totalCount = list.size,
            autoCategorizedCount = list.count { it.transaction.isClarified || it.transaction.confidenceTier != "MANUAL" },
        )
    }

    override suspend fun monthlySpendTrend(limit: Int): List<MonthlySpend> =
        rows
            .filter { it.transaction.txnType == "DEBIT" }
            .groupBy {
                YearMonth.from(Instant.ofEpochMilli(it.transaction.timestamp).atZone(ZoneId.systemDefault()))
            }
            .map { (ym, list) ->
                MonthlySpend(
                    monthLabel = ym.toString(),
                    totalAmount = list.sumOf { it.transaction.amount },
                    monthStart = ym.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                )
            }
            .sortedByDescending { it.monthStart }
            .take(limit)

    override suspend fun potentialRecurringMerchants(): List<RecurringMerchantRow> =
        rows
            .filter { it.transaction.txnType == "DEBIT" }
            .groupBy { it.transaction.merchantName }
            .filterValues { it.size >= 2 }
            .map { (name, list) ->
                RecurringMerchantRow(
                    merchantName = name,
                    avgAmount = list.map { it.transaction.amount }.average(),
                    txnCount = list.size,
                    firstSeen = list.minOf { it.transaction.timestamp },
                    lastSeen = list.maxOf { it.transaction.timestamp },
                )
            }
            .sortedByDescending { it.txnCount }

    override suspend fun debitsBetween(start: Long, end: Long): List<DebitTransaction> =
        inRangeDebits(start, end)
            .map {
                DebitTransaction(
                    id = it.transaction.id,
                    amount = it.transaction.amount,
                    merchantName = it.transaction.merchantName,
                    cleanPayee = it.transaction.cleanPayee,
                    categoryId = it.transaction.categoryId,
                    timestamp = it.transaction.timestamp,
                )
            }
            .sortedByDescending { it.amount }

    override suspend fun merchantFrequency(start: Long, end: Long): List<MerchantFrequencyRow> =
        inRangeDebits(start, end)
            .groupBy { it.transaction.merchantName }
            .map { (name, list) -> MerchantFrequencyRow(name, list.size) }

    override suspend fun spendByMerchant(merchant: String, start: Long, end: Long): Double =
        inRangeDebits(start, end)
            .filter {
                it.transaction.merchantName.contains(merchant, ignoreCase = true) ||
                    (it.transaction.cleanPayee ?: "").contains(merchant, ignoreCase = true)
            }
            .sumOf { it.transaction.amount }

    override suspend fun totalDebitsBetween(start: Long, end: Long): Double =
        inRangeDebits(start, end).sumOf { it.transaction.amount }

    override suspend fun debitCountBetween(start: Long, end: Long): Int =
        inRangeDebits(start, end).size

    override suspend fun maxDebitBetween(start: Long, end: Long): Double =
        inRangeDebits(start, end).maxOfOrNull { it.transaction.amount } ?: 0.0
}

/** In-memory CategoryDao backed by [SampleData.categories]. */
class FakeCategoryDao(
    private val cats: MutableList<CategoryEntity> = SampleData.categories.toMutableList(),
) : CategoryDao {

    override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(cats.toList())

    override suspend fun getAllOnce(): List<CategoryEntity> = cats.toList()

    override suspend fun getById(id: Long): CategoryEntity? = cats.firstOrNull { it.id == id }

    override suspend fun getByName(name: String): CategoryEntity? = cats.firstOrNull { it.name == name }

    override suspend fun insertAll(entities: List<CategoryEntity>) {
        cats.addAll(entities)
    }

    override suspend fun incrementUsage(categoryId: Long) {
        val idx = cats.indexOfFirst { it.id == categoryId }
        if (idx >= 0) cats[idx] = cats[idx].copy(usageCount = cats[idx].usageCount + 1)
    }

    override suspend fun deleteAll() {
        cats.clear()
    }
}

class FakeClarificationHistoryDao : ClarificationHistoryDao {
    override suspend fun insert(entity: ClarificationHistoryEntity): Long = 0
    override suspend fun getRecent(limit: Int): List<ClarificationHistoryEntity> = emptyList()
    override suspend fun count(): Int = 0
}

class FakeTemplateCacheDao : TemplateCacheDao {
    override suspend fun findByPattern(packageName: String, patternHash: String): TemplateCacheEntity? = null
    override suspend fun upsert(entity: TemplateCacheEntity): Long = 0
    override suspend fun recordHit(id: Long, nowEpochMillis: Long) {}
    override suspend fun getAll(): List<TemplateCacheEntity> = emptyList()
    override suspend fun deleteById(id: Long) {}
}

/** In-memory MonitoredPackageDao backed by [SampleData.monitoredPackages]. */
class FakeMonitoredPackageDao(
    private val pkgs: MutableList<MonitoredPackageEntity> = SampleData.monitoredPackages.toMutableList(),
) : MonitoredPackageDao {

    override fun observeAll(): Flow<List<MonitoredPackageEntity>> = flowOf(pkgs.toList())

    override suspend fun getAllOnce(): List<MonitoredPackageEntity> = pkgs.toList()

    override suspend fun getEnabled(): List<MonitoredPackageEntity> = pkgs.filter { it.isEnabled }

    override suspend fun getByPackageName(packageName: String): MonitoredPackageEntity? =
        pkgs.firstOrNull { it.packageName == packageName }

    override suspend fun insertAll(entities: List<MonitoredPackageEntity>) {
        pkgs.addAll(entities)
    }

    override suspend fun insert(entity: MonitoredPackageEntity): Long {
        pkgs.add(entity)
        return entity.id
    }

    override suspend fun update(entity: MonitoredPackageEntity) {
        val idx = pkgs.indexOfFirst { it.id == entity.id }
        if (idx >= 0) pkgs[idx] = entity
    }

    override suspend fun deleteById(id: Long) {
        pkgs.removeAll { it.id == id }
    }
}
