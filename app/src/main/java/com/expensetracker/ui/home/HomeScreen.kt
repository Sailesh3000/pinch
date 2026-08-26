package com.expensetracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.core.common.Formatters
import com.expensetracker.core.model.Transaction
import com.expensetracker.ui.components.TransactionRow
import com.expensetracker.ui.detail.TransactionDetailSheet
import com.expensetracker.ui.manualentry.ManualEntryDialog
import com.expensetracker.ui.theme.Coral
import com.expensetracker.ui.theme.CoralSoft
import com.expensetracker.ui.theme.ElectricMint
import com.expensetracker.ui.theme.GradientTealEnd
import com.expensetracker.ui.theme.GradientTealStart
import com.expensetracker.ui.theme.MintGlow
import com.expensetracker.ui.theme.MistTeal
import com.expensetracker.ui.theme.MonoFont
import com.expensetracker.ui.theme.PinchTeal
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showManualEntry by remember { mutableStateOf(false) }

    Scaffold(
        // The outer nav host already applies system-bar insets to every tab;
        // this Scaffold exists only to place the FAB, so don't let it add a
        // second top inset that pushes the header below Insights/Review.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showManualEntry = true },
                containerColor = PinchTeal,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add manual transaction")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Branded Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "pinch",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.8).sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "spending, sorted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Month Summary Card
            MonthSummaryHeader(summary = uiState.monthSummary)

            // Search and Category Filter
            SearchAndFilterBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                categories = uiState.categories,
                selectedCategoryId = uiState.selectedCategoryId,
                onCategorySelect = viewModel::setCategoryFilter,
            )

            // Content Area
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = PinchTeal)
                    }
                }
                uiState.transactions.isEmpty() -> {
                    EmptyState(
                        isFiltered = uiState.searchQuery.isNotBlank() || uiState.selectedCategoryId != null
                    )
                }
                else -> {
                    TransactionFeed(
                        transactions = uiState.transactions,
                        onTransactionClick = { selectedTransaction = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    selectedTransaction?.let { transaction ->
        TransactionDetailSheet(
            transaction = transaction,
            categories = uiState.categories,
            onDismiss = { selectedTransaction = null },
            onUpdateCategory = viewModel::updateCategory,
            onDelete = {
                viewModel.deleteTransaction(transaction.id)
                selectedTransaction = null
            },
        )
    }

    if (showManualEntry) {
        ManualEntryDialog(
            categories = uiState.categories,
            onDismiss = { showManualEntry = false },
            onSave = { amount, merchant, categoryId, txnType ->
                viewModel.addManualTransaction(amount, merchant, categoryId, txnType, System.currentTimeMillis())
                showManualEntry = false
            },
        )
    }
}

@Composable
private fun MonthSummaryHeader(
    summary: com.expensetracker.core.math.DeterministicMathEngine.MonthSpendSummary?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(ElectricMint, GradientTealStart, GradientTealEnd),
                        start = Offset(0f, 0f),
                        end = Offset(1100f, 900f),
                    )
                )
        ) {
            // Soft glow blob — decorative depth, not a hard shape.
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 50.dp, y = (-60).dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(ElectricMint.copy(alpha = 0.55f), Color.Transparent)
                        ),
                        shape = CircleShape,
                    )
            )
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "SPENT THIS MONTH",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = Formatters.money(summary?.currentMonthTotal ?: 0.0),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(10.dp))
                summary?.let { s ->
                    val delta = s.deltaAmount
                    val up = delta > 0
                    if (s.previousMonthTotal > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (up) CoralSoft.copy(alpha = 0.22f) else MistTeal.copy(alpha = 0.22f)
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Icon(
                                imageVector = if (up) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                contentDescription = null,
                                tint = if (up) Color(0xFFFFB3A0) else Color(0xFFAEF1E1),
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = buildString {
                                    append(Formatters.money(abs(delta)))
                                    append(if (up) " more vs last month" else " less vs last month")
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = Color.White.copy(alpha = 0.95f),
                            )
                        }
                    } else {
                        Text(
                            text = "${s.transactionCount} transactions captured",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchAndFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    categories: List<com.expensetracker.core.model.Category>,
    selectedCategoryId: Long?,
    onCategorySelect: (Long?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        // Pill-shaped search bar
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            placeholder = {
                Text(
                    "Search transactions or payees...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = PinchTeal,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = PinchTeal,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val allSelected = selectedCategoryId == null
            FilterChip(
                selected = allSelected,
                onClick = { onCategorySelect(null) },
                label = { Text("All") },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MintGlow,
                    selectedLabelColor = PinchTeal,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = allSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = PinchTeal,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.5.dp,
                ),
            )
            categories.forEach { category ->
                val selected = selectedCategoryId == category.id
                FilterChip(
                    selected = selected,
                    onClick = {
                        onCategorySelect(if (selected) null else category.id)
                    },
                    label = { Text(category.name) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MintGlow,
                        selectedLabelColor = PinchTeal,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = PinchTeal,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.5.dp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TransactionFeed(
    transactions: List<Transaction>,
    onTransactionClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = transactions.groupBy { transaction ->
        Formatters.dayGroupLabel(transaction.timestamp)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
    ) {
        groups.forEach { (label, groupedTransactions) ->
            item(key = "header_$label") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                }
            }
            items(
                items = groupedTransactions,
                key = { it.id },
            ) { transaction ->
                TransactionRow(
                    transaction = transaction,
                    onClick = { onTransactionClick(transaction) },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(isFiltered: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .scale(if (!isFiltered) pulseScale else 1f)
                .clip(CircleShape)
                .background(MistTeal),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ReceiptLong,
                contentDescription = null,
                tint = PinchTeal,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = if (isFiltered) {
                "No matching transactions"
            } else {
                "Listening for transactions..."
            },
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isFiltered) {
                "Try searching for another merchant or resetting category filters."
            } else {
                "Bank and UPI alert notifications will appear here automatically."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

