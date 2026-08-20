package com.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private fun iconFor(iconKey: String?): ImageVector = when (iconKey) {
    "FOOD" -> Icons.Filled.Restaurant
    "GROCERIES" -> Icons.Filled.ShoppingCart
    "TRANSPORTATION" -> Icons.Filled.DirectionsBus
    "SHOPPING" -> Icons.Filled.ShoppingBag
    "BILLS" -> Icons.Filled.ReceiptLong
    "RENT" -> Icons.Filled.Home
    "ENTERTAINMENT" -> Icons.Filled.Movie
    "HEALTH" -> Icons.Filled.MedicalServices
    "INVESTMENT" -> Icons.Filled.TrendingUp
    "TRANSFER" -> Icons.Filled.SwapHoriz
    "INCOME" -> Icons.Filled.AccountBalanceWallet
    else -> Icons.Filled.HelpOutline
}

private fun parseColor(hex: String?, fallback: Color): Color =
    runCatching {
        Color(android.graphics.Color.parseColor(hex ?: ""))
    }.getOrDefault(fallback)

@Composable
fun CategoryIcon(
    iconKey: String?,
    colorHex: String?,
    modifier: Modifier = Modifier,
) {
    val baseColor = parseColor(colorHex, Color(0xFF1B8A6B))
    val backgroundColor = baseColor.copy(alpha = 0.12f)
    
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconFor(iconKey),
            contentDescription = null,
            tint = baseColor,
            modifier = Modifier.size(22.dp),
        )
    }
}

