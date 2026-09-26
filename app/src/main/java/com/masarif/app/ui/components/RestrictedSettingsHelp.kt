package com.masarif.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.masarif.app.ui.Permissions

/** خطوات فتح "الإعدادات المقيّدة" على أندرويد 13+ للتطبيقات المثبّتة من خارج المتجر. */
@Composable
fun RestrictedSettingsHelp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    SectionCard(title = "الإعدادات المقيّدة (أندرويد 13+)", modifier = modifier) {
        Text(
            "لأن التطبيق مثبّت من ملف APK وليس من المتجر، قد يحجب أندرويد صلاحيات الرسائل حتى تسمح بها يدوياً:",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        listOf(
            "1. افتح «معلومات التطبيق» بالزر أدناه.",
            "2. اضغط النقاط الثلاث ⋮ في أعلى الشاشة.",
            "3. اختر «السماح بالإعدادات المقيّدة» وأكّد.",
            "4. ارجع إلى «الأذونات» → «الرسائل النصية» → «السماح».",
            "5. عد إلى التطبيق واضغط «منح الصلاحيات» مرة أخرى.",
        ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { Permissions.openAppInfo(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("فتح معلومات التطبيق")
        }
    }
}
