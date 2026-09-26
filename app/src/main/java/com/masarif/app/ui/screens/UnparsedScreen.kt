@file:OptIn(ExperimentalMaterial3Api::class)

package com.masarif.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masarif.app.MasarifApp
import com.masarif.app.ui.Fmt
import com.masarif.app.ui.components.EmptyState
import com.masarif.app.ui.components.SectionCard
import kotlinx.coroutines.launch

@Composable
fun UnparsedScreen(app: MasarifApp, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val list by app.repository.observeUnparsed().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("رسائل غير مفهومة (${list.size})") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val n = app.repository.retryUnparsed()
                            snackbar.showSnackbar(if (n > 0) "نجح تحليل $n رسالة" else "لا جديد: الصيغ ما زالت مجهولة")
                        }
                    }) { Icon(Icons.Filled.Refresh, contentDescription = "إعادة محاولة التحليل") }
                },
            )
        },
    ) { padding ->
        if (list.isEmpty()) {
            EmptyState("لا رسائل غير مفهومة. كل رسائل الإنماء المالية فُهمت.", Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            items(list, key = { it.id }) { u ->
                SectionCard(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${u.sender} · ${Fmt.dateTime(u.receivedAt)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text("السبب: ${u.reason}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { scope.launch { app.repository.deleteUnparsed(u.id) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف")
                        }
                    }
                    Text(u.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
