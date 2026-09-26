@file:OptIn(ExperimentalMaterial3Api::class)

package com.masarif.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.masarif.app.MasarifApp
import com.masarif.app.data.db.TransactionEntity
import com.masarif.app.ui.Fmt
import com.masarif.app.ui.components.ConfirmDialog
import com.masarif.app.ui.components.SectionCard
import com.masarif.app.ui.components.Selector
import com.masarif.core.Channel
import com.masarif.core.Direction
import com.masarif.core.Money
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** تعديل عملية موجودة أو إضافة عملية يدوية (id = -1). */
@Composable
fun TransactionEditScreen(app: MasarifApp, id: Long, snackbar: SnackbarHostState, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val categories by app.repository.observeCategories().collectAsState(initial = emptyList())
    val isNew = id < 0

    var loaded by remember { mutableStateOf(isNew) }
    var existing by remember { mutableStateOf<TransactionEntity?>(null) }
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(Direction.EXPENSE) }
    var channel by remember { mutableStateOf(Channel.POS) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var card by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0).format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        if (!isNew) {
            val t = app.repository.getTransaction(id)
            if (t != null) {
                existing = t
                amount = Money.format(t.amountHalalas, grouping = false)
                merchant = t.merchantClean
                direction = t.direction
                channel = t.channel
                categoryId = t.categoryId
                card = t.cardLast4.orEmpty()
                country = t.country.orEmpty()
                val ldt = Fmt.local(t.dateTime)
                date = ldt.toLocalDate()
                time = ldt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
            }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "إضافة عملية يدوية" else "تعديل العملية") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } },
                actions = {
                    if (!isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "حذف") }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = amount, onValueChange = { amount = it }, label = { Text("المبلغ (ر.س)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("التاجر") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Selector(label = "الاتجاه", options = Direction.entries, selected = direction, labelOf = { Fmt.direction(it) }, onSelect = { direction = it })
            Selector(
                label = "التصنيف",
                options = categories.filter { it.isIncome == (direction == Direction.INCOME) }.ifEmpty { categories },
                selected = categories.firstOrNull { it.id == categoryId },
                labelOf = { "${it.icon} ${it.nameAr}" },
                onSelect = { categoryId = it.id },
            )
            Selector(label = "القناة", options = Channel.entries, selected = channel, labelOf = { Fmt.channel(it) }, onSelect = { channel = it })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                    Text(date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)))
                }
                OutlinedTextField(
                    value = time, onValueChange = { time = it }, label = { Text("الوقت HH:mm") }, singleLine = true, modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(value = card, onValueChange = { card = it.take(4) }, label = { Text("آخر 4 أرقام من البطاقة (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = country, onValueChange = { country = it }, label = { Text("الدولة (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            existing?.rawSms?.takeIf { it.isNotBlank() }?.let { raw ->
                SectionCard(title = "نص الرسالة الأصلي") {
                    Text(raw, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val halalas = Money.parseHalalas(amount)
                    val parsedTime = runCatching { LocalTime.parse(time, DateTimeFormatter.ofPattern("H:mm", Locale.US)) }.getOrNull()
                    val cat = categoryId
                    when {
                        halalas == null || halalas <= 0 -> error = "أدخل مبلغاً صحيحاً أكبر من صفر."
                        merchant.isBlank() -> error = "أدخل اسم التاجر."
                        cat == null -> error = "اختر تصنيفاً."
                        parsedTime == null -> error = "الوقت بصيغة HH:mm مثل 15:30."
                        else -> {
                            val millis = LocalDateTime.of(date, parsedTime).atZone(Fmt.zone).toInstant().toEpochMilli()
                            scope.launch {
                                val e = existing
                                if (e == null) {
                                    app.repository.addManualTransaction(millis, halalas, direction, merchant.trim(), cat, channel, card.trim().ifBlank { null }, country.trim().ifBlank { null })
                                } else {
                                    app.repository.updateTransaction(
                                        e.copy(
                                            dateTime = millis, amountHalalas = halalas, direction = direction, merchantClean = merchant.trim(),
                                            categoryId = cat, channel = channel, cardLast4 = card.trim().ifBlank { null }, country = country.trim().ifBlank { null },
                                        )
                                    )
                                }
                                snackbar.showSnackbar("تم الحفظ")
                                onClose()
                            }
                        }
                    }
                },
            ) { Text("حفظ") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("موافق") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("إلغاء") } },
        ) { DatePicker(state = state) }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "حذف العملية",
            text = "سيُحذف هذا السجل نهائياً. إعادة مسح الرسائل قد تعيده إن كانت رسالته ما زالت في الجهاز.",
            confirmText = "حذف", destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch { app.repository.deleteTransaction(id); snackbar.showSnackbar("حُذفت العملية"); onClose() }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
