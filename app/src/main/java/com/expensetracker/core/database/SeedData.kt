package com.expensetracker.core.database

import com.expensetracker.core.database.entity.CategoryEntity
import com.expensetracker.core.database.entity.MonitoredPackageEntity

/**
 * Default seed data for the Room database.
 * Categories follow the extraction enum in spec §6.1; packages follow FR-INGEST-02.
 */
object SeedData {

    fun defaultCategories(): List<CategoryEntity> = listOf(
        CategoryEntity(name = "Food & Dining", iconKey = "FOOD", colorHex = "#E8590C"),
        CategoryEntity(name = "Groceries", iconKey = "GROCERIES", colorHex = "#2B8A3E"),
        CategoryEntity(name = "Transportation", iconKey = "TRANSPORTATION", colorHex = "#0CA678"),
        CategoryEntity(name = "Shopping", iconKey = "SHOPPING", colorHex = "#E64980"),
        CategoryEntity(name = "Bills & Utilities", iconKey = "BILLS", colorHex = "#F08C00"),
        CategoryEntity(name = "Rent", iconKey = "RENT", colorHex = "#1971C2"),
        CategoryEntity(name = "Entertainment", iconKey = "ENTERTAINMENT", colorHex = "#7048E8"),
        CategoryEntity(name = "Health & Medical", iconKey = "HEALTH", colorHex = "#C2255C"),
        CategoryEntity(name = "Investment", iconKey = "INVESTMENT", colorHex = "#1098AD"),
        CategoryEntity(name = "Friend/Transfer", iconKey = "TRANSFER", colorHex = "#868E96"),
        CategoryEntity(name = "Salary/Income", iconKey = "INCOME", colorHex = "#2F9E44"),
        CategoryEntity(name = "Uncategorized", iconKey = "UNCATEGORIZED", colorHex = "#ADB5BD")
    )

    /**
     * Whitelisted financial packages per FR-INGEST-02. The user can add custom
     * packages at runtime via Settings; entries are stored in `monitored_packages`.
     */
    fun defaultPackages(): List<MonitoredPackageEntity> = listOf(
        MonitoredPackageEntity(packageName = "com.google.android.apps.messaging", appLabel = "Google Messages"),
        MonitoredPackageEntity(packageName = "com.samsung.android.messaging", appLabel = "Samsung Messages"),
        MonitoredPackageEntity(packageName = "com.android.mms", appLabel = "Default SMS App"),

        MonitoredPackageEntity(packageName = "com.phonepe.app", appLabel = "PhonePe"),
        MonitoredPackageEntity(packageName = "com.google.android.apps.nbu.paisa.user", appLabel = "Google Pay"),
        MonitoredPackageEntity(packageName = "net.one97.paytm", appLabel = "Paytm"),
        MonitoredPackageEntity(packageName = "com.dreamplug.androidapp", appLabel = "CRED"),
        MonitoredPackageEntity(packageName = "in.org.npci.upiapp", appLabel = "BHIM UPI"),

        MonitoredPackageEntity(packageName = "com.snapwork.hdfc", appLabel = "HDFC MobileBanking"),
        MonitoredPackageEntity(packageName = "com.icici.mobile", appLabel = "ICICI iMobile"),
        MonitoredPackageEntity(packageName = "com.sbi.SBIFreedomPlus", appLabel = "SBI YONO"),
        MonitoredPackageEntity(packageName = "com.axis.mobile", appLabel = "Axis Mobile"),
        MonitoredPackageEntity(packageName = "com.kotak.lite", appLabel = "Kotak 811"),
        MonitoredPackageEntity(packageName = "com.hdfc.banking", appLabel = "HDFC Bank PayZapp"),

        MonitoredPackageEntity(packageName = "com.sc.mobile.android.mobilebanking", appLabel = "Standard Chartered"),
        MonitoredPackageEntity(packageName = "com.chase.sig.android", appLabel = "Chase Mobile"),
        MonitoredPackageEntity(packageName = "com.citi.citimobile", appLabel = "Citi Mobile"),
        MonitoredPackageEntity(packageName = "com.revolut.revolut", appLabel = "Revolut"),
        MonitoredPackageEntity(packageName = "uk.co.monzo.android", appLabel = "Monzo"),
        MonitoredPackageEntity(packageName = "com.americanexpress.android.acctsvcs.us", appLabel = "Amex"),

        // Test package for emulator validation (cmd notification post sends from com.android.shell)
        MonitoredPackageEntity(packageName = "com.android.shell", appLabel = "Shell (Test)"),
    )
}
