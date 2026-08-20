package com.expensetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.expensetracker.core.model.Category
import com.expensetracker.ui.theme.MintGlow
import com.expensetracker.ui.theme.PinchTeal

/**
 * Reusable category chip row. Used by the
 * detail sheet, review screen, and manual-entry dialog.
 */
@Composable
fun CategoryChipPicker(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        categories.forEach { category ->
            val selected = selectedCategoryId == category.id
            FilterChip(
                selected = selected,
                onClick = { onSelect(category.id) },
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

