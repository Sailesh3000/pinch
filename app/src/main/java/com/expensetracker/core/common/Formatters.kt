package com.expensetracker.core.common

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {

    private val moneyFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    /** "₹1,240.50" — deterministic formatting only, never arithmetic. */
    fun money(amount: Double, currency: String = "INR"): String {
        val symbol = when (currency) {
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "INR" -> "₹"
            else -> "${currency} "
        }
        return symbol + moneyFormat.format(amount)
    }

    fun moneySigned(amount: Double, currency: String = "INR"): String =
        if (amount < 0) "-${money(-amount, currency)}" else money(amount, currency)

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val dayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

    fun timeOfDay(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        timeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun fullDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dayFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun dayGroupLabel(epochMillis: Long, nowEpochMillis: Long = System.currentTimeMillis()): String {
        val zone = ZoneId.systemDefault()
        val dayStart = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
        return when (dayStart) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> fullDate(epochMillis)
        }
    }
}
