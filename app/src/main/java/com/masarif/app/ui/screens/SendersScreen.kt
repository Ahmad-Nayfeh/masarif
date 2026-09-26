@file:OptIn(ExperimentalMaterial3Api::class)

package com.masarif.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masarif.app.MasarifApp
import com.masarif.app.ui.components.SectionCard
import kotlinx.coroutines.launch

/** المرسلون: إضافة مرسل تتحقق أولاً من وجود رسائله في صندوق الوارد. */
@Composable
fun SendersScreen(app: MasarifApp, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val senders by app.settings.senders.collectAsState(initial = emptyList())
    var newSender by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المرسلون") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            SectionCard {
                Text(
                    "تُقبل الرسالة إذا احتوى اسم مرسلها أحد هذه الأسماء (بلا حساسية لحالة الأحرف). التطبيق يفهم حالياً صيغ رسائل بنك الإنماء (alinma) فقط؛ رسائل أي مرسل آخر تُحفظ في «غير مفهومة» دون احتساب.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            senders.forEach { s ->
                ListItem(
                    headlineContent = { Text(s) },
                    trailingContent = {
                        IconButton(onClick = { scope.launch { app.settings.setSenders(senders - s) } }, enabled = senders.size > 1) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف")
                        }
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSender, onValueChange = { newSender = it; error = null }, label = { Text("مرسل جديد") },
                    singleLine = true, modifier = Modifier.weight(1f), enabled = !checking,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !checking && newSender.isNotBlank(),
                    onClick = {
                        val s = newSender.trim()
                        if (senders.any { it.equals(s, true) }) { error = "مضاف بالفعل."; return@Button }
                        checking = true
                        scope.launch {
                            val n = runCatching { app.scanner.countMessagesFrom(s) }.getOrDefault(0)
                            checking = false
                            if (n == 0) {
                                error = "لم أجد أي رسالة من «$s» في صندوق الوارد."
                            } else {
                                app.settings.setSenders(senders + s)
                                newSender = ""
                                snackbar.showSnackbar("أُضيف $s ($n رسالة). اضغط «إعادة مسح الرسائل» في الإعدادات لاستيرادها.")
                            }
                        }
                    },
                ) { Text(if (checking) "…" else "تحقق وأضف") }
            }
            error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
