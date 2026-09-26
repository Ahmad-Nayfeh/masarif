@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.masarif.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.masarif.app.data.db.CategoryEntity
import com.masarif.core.DefaultCategories

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "تأكيد",
    dismissText: String = "إلغاء",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    )
}

/** قائمة اختيار بسيطة بزر يفتح DropdownMenu. */
@Composable
fun <T> Selector(
    label: String,
    options: List<T>,
    selected: T?,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "اختر",
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.let(labelOf) ?: placeholder, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(text = { Text(labelOf(opt)) }, onClick = { open = false; onSelect(opt) })
                }
            }
        }
    }
}

@Composable
fun CategoryChip(category: CategoryEntity?, modifier: Modifier = Modifier) {
    val color = category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.size(6.dp))
        Text(
            text = category?.let { "${it.icon} ${it.nameAr}" } ?: "غير مصنّف",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * ورقة سفلية بشبكة التصنيفات: لمسة واحدة تختار. فئة الاشتراكات مثبّتة أولاً وبارزة، وزر
 * "+ فئة جديدة" ينشئ فئة في مكانها بلا الذهاب للإعدادات.
 */
@Composable
fun CategoryPickerSheet(
    title: String,
    categories: List<CategoryEntity>,
    onPick: (CategoryEntity) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    /** إن كانت null يختفي زر الإنشاء. تُستدعى ثم تُختار الفئة الجديدة مباشرة. */
    onCreate: ((CategoryEntity) -> Unit)? = null,
    incomeOnly: Boolean = false,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var creating by remember { mutableStateOf(false) }
    val shown = remember(categories, incomeOnly) {
        categories.filter { it.isIncome == incomeOnly }
            .sortedWith(compareByDescending<CategoryEntity> { it.catKey == DefaultCategories.SUBSCRIPTIONS_KEY }.thenBy { it.catKey == DefaultCategories.UNCATEGORIZED_KEY }.thenBy { it.sortOrder })
    }
    val subscriptionsHint = DefaultCategories.all.first { it.key == DefaultCategories.SUBSCRIPTIONS_KEY }.hint

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                items(shown, key = { it.id }, span = { c -> GridItemSpan(if (c.catKey == DefaultCategories.SUBSCRIPTIONS_KEY) 3 else 1) }) { c ->
                    val pinned = c.catKey == DefaultCategories.SUBSCRIPTIONS_KEY
                    if (pinned) {
                        Row(
                            Modifier
                                .clickable { onPick(c) }
                                .background(Color(c.color).copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(c.color), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(40.dp).background(Color(c.color).copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
                                Text(c.icon, style = MaterialTheme.typography.titleLarge)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(c.nameAr, fontWeight = FontWeight.Bold)
                                if (subscriptionsHint != null) Text(subscriptionsHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        CategoryTile(c) { onPick(c) }
                    }
                }
                if (onCreate != null) {
                    item(key = "new") {
                        Column(
                            Modifier
                                .clickable { creating = true }
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .padding(vertical = 12.dp, horizontal = 6.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, contentDescription = null) }
                            Spacer(Modifier.height(6.dp))
                            Text("فئة جديدة", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    if (creating && onCreate != null) {
        NewCategoryDialog(
            isIncome = incomeOnly,
            onSave = { c -> creating = false; onCreate(c) },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun CategoryTile(c: CategoryEntity, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 6.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(34.dp).background(Color(c.color).copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
            Text(c.icon, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        Text(c.nameAr, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** إنشاء فئة سريعة: اسم، رمز (مقترحات أو أي رمز تكتبه)، لون. */
@Composable
fun NewCategoryDialog(isIncome: Boolean, onSave: (CategoryEntity) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(DefaultCategories.emojiSuggestions.first()) }
    var color by remember { mutableStateOf(DefaultCategories.palette[8]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فئة جديدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الفئة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("الرمز", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DefaultCategories.emojiSuggestions.forEach { e ->
                        Box(
                            Modifier.size(36.dp)
                                .background(if (e == icon) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .clickable { icon = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e) }
                    }
                }
                OutlinedTextField(value = icon, onValueChange = { icon = it.take(4) }, label = { Text("أو اكتب رمزاً") }, singleLine = true, modifier = Modifier.width(140.dp))
                Text("اللون", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DefaultCategories.palette.forEach { c ->
                        Box(
                            Modifier.size(26.dp).background(Color(c), CircleShape)
                                .border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = c }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(CategoryEntity(nameAr = name.trim(), icon = icon.ifBlank { "🏷️" }, color = color, isIncome = isIncome, sortOrder = 0))
            }) { Text("إنشاء") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
