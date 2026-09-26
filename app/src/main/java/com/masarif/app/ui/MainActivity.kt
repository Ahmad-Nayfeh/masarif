@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.masarif.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.masarif.app.MasarifApp
import com.masarif.app.ui.theme.MasarifTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** يزداد عند كل طلب لفتح قائمة الانتظار (من الإشعار). */
    val openQueueRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.action == ACTION_OPEN_QUEUE) openQueueRequests.value++
        val app = application as MasarifApp
        setContent {
            val dark by app.settings.darkMode.collectAsState(initial = true)
            MasarifTheme(dark = dark) {
                // يجعل testTag يظهر كـ resource-id لأدوات الأتمتة (اختبار المحاكي في CI).
                Box(Modifier.semantics { testTagsAsResourceId = true }) {
                    AppRoot(app = app, openQueueRequests = openQueueRequests)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_OPEN_QUEUE) openQueueRequests.value++
    }

    override fun onStart() {
        super.onStart()
        android.util.Log.i("MainActivity", "onStart (recreated=${isChangingConfigurations})")
    }

    override fun onDestroy() {
        android.util.Log.i("MainActivity", "onDestroy (changingConfig=${isChangingConfigurations}, finishing=$isFinishing)")
        super.onDestroy()
    }

    companion object {
        const val ACTION_OPEN_QUEUE = "com.masarif.app.OPEN_QUEUE"
    }
}
