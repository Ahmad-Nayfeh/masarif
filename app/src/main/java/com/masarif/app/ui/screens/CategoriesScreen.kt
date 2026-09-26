@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.masarif.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masarif.app.MasarifApp
import com.masarif.app.data.db.CategoryEntity
import com.masarif.app.ui.components.EmptyState
import com.masarif.app.ui.components.Selector
import com.masarif.core.DefaultCategories
import kotlinx.coroutines.launch

/** إدارة التصنيفات: إضافة، إعادة تسمية، دمج، حذف مع نقل عملياتها وقواعدها لتصنيف آخر. */
@Composable
fun CategoriesScreen(app: MasarifApp, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val categories by app.repository.observeCategories().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var removing by remember { mutableStateOf<CategoryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التصنيفات (${categories.size})") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = CategoryEntity(nameAr = "", icon = "🏷️", color = DefaultCategories.palette.first(), isIncome = false, sortOrder = 0)
            }) { Icon(Icons.Filled.Add, contentDescription = "إضافة تصنيف") }
        },
    ) { padding ->
        if (categories.isEmpty()) {
            EmptyState("لا تصنيفات.", Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(categories, key = { it.id }) { c ->
                ListItem(
                    leadingContent = {
                        Box(Modifier.size(36.dp).background(Color(c.color).copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) { Text(c.icon) }
                    },
                    headlineContent = { Text(c.nameAr, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text((if (c.isIncome) "دخل" else "صرف") + (if (c.isProtected) " · أساسي" else ""), style = MaterialTheme.typography.bodySmall) },
                    trailingContent = {
                        if (!c.isProtected) IconButton(onClick = { removing = c }) { Icon(Icons.Filled.Delete, contentDescription = "حذف/دمج") }
                    },
                    modifier = Modifier.clickable { editing = c },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }

    editing?.let { c ->
        CategoryEditDialog(
            category = c,
            onSave = { saved -> editing = null; scope.launch { app.repository.saveCategory(saved); snackbar.showSnackbar("تم الحفظ") } },
            onDismiss = { editing = null },
        )
    }

    removing?.let { c ->
        MergeDialog(
            app = app, category = c, others = categories.filter { it.id != c.id },
            onDone = { target ->
                removing = null
                scope.launch {
                    val ok = app.repository.deleteCategory(c.id, target.id)
                    snackbar.showSnackbar(if (ok) "نُقلت عمليات «${c.nameAr}» إلى «${target.nameAr}» وحُذف التصنيف" else "تعذّر الحذف")
                }
            },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun CategoryEditDialog(category: CategoryEntity, onSave: (CategoryEntity) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(category.nameAr) }
    var icon by remember { mutableStateOf(category.icon) }
    var color by remember { mutableStateOf(category.color) }
    var isIncome by remember { mutableStateOf(category.isIncome) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category.id == 0L) "تصنيف جديد" else "تعديل التصنيف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = icon, onValueChange = { icon = it.take(4) }, label = { Text("رمز") }, singleLine = true, modifier = Modifier.width(88.dp))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text("اللون", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DefaultCategories.palette.forEach { c ->
                        Box(
                            Modifier.size(28.dp).background(Color(c), CircleShape)
                                .border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = c }
                        )
                    }
                }
                if (!category.isProtected) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("تصنيف دخل")
                        Switch(checked = isIncome, onCheckedChange = { isIncome = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(category.copy(nameAr = name.trim(), icon = icon.ifBlank { "🏷️" }, color = color, isIncome = isIncome)) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun MergeDialog(app: MasarifApp, category: CategoryEntity, others: List<CategoryEntity>, onDone: (CategoryEntity) -> Unit, onDismiss: () -> Unit) {
    var target by remember { mutableStateOf(others.firstOrNull { it.catKey == DefaultCategories.UNCATEGORIZED_KEY } ?: others.firstOrNull()) }
    var usage by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(category.id) { usage = app.repository.categoryUsage(category.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حذف «${category.nameAr}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                usage?.let { (tx, rules) -> Text("فيه $tx عملية و $rules قاعدة. ستُنقل كلها إلى التصنيف الذي تختاره (دمج).") }
                Selector(label = "انقل إلى", options = others, selected = target, labelOf = { "${it.icon} ${it.nameAr}" }, onSelect = { target = it })
            }
        },
        confirmButton = { TextButton(enabled = target != null, onClick = { target?.let(onDone) }) { Text("نقل وحذف", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
