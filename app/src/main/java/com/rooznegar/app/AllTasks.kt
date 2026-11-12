package com.rooznegar.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Makes Arabic/Persian letter and digit variants compare equal so searching is forgiving. */
fun normalizeSearch(s: String): String {
    val sb = StringBuilder()
    for (ch in s.lowercase()) {
        sb.append(
            when (ch) {
                'ي' -> 'ی'
                'ك' -> 'ک'
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                else -> ch
            }
        )
    }
    return sb.toString()
}

@Composable
fun AllTasksScreen(vm: MainViewModel, today: LocalDate, onBack: () -> Unit, onEdit: (TaskEntity) -> Unit) {
    val all by vm.allTasks.collectAsState()
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    val todayS = today.toString()
    val nq = normalizeSearch(q.trim())
    val list = all.filter { t ->
        (nq.isEmpty() || normalizeSearch(t.title).contains(nq)) && when (filter) {
            "open" -> !t.completed
            "done" -> t.completed
            "late" -> !t.completed && t.scheduledDate < todayS
            else -> true
        }
    }
    val groups = list.groupBy { it.scheduledDate }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("بازگشت") }
            Text(
                "همه‌ی کارها (${faNum(list.size)})", Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
        }
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text("جستجو در عنوان کارها...") }, modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "همه", "open" to "باقی‌مانده", "done" to "انجام‌شده", "late" to "معوق").forEach { (k, label) ->
                if (filter == k) {
                    Button(onClick = { filter = k }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text(label, fontSize = 13.sp)
                    }
                } else {
                    OutlinedButton(onClick = { filter = k }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text(label, fontSize = 13.sp)
                    }
                }
            }
        }
        if (list.isEmpty()) {
            Text("کاری پیدا نشد.", Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f)) {
            for ((date, tasks) in groups) {
                item(key = "h$date") {
                    Text(
                        jalaliFull(LocalDate.parse(date)) + " · " + faNum(tasks.count { !it.completed }) +
                            " باقی‌مانده، " + faNum(tasks.count { it.completed }) + " انجام‌شده",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                    )
                }
                items(tasks, key = { it.id }) { t ->
                    Row(Modifier.fillMaxWidth().clickable { onEdit(t) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = t.completed, onCheckedChange = { vm.setDone(t.id, it) })
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (t.priority) "🚩 " else "") + t.title,
                                textDecoration = if (t.completed) TextDecoration.LineThrough else null,
                                color = if (t.completed) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                            )
                            val late = !t.completed && t.scheduledDate < todayS
                            Text(
                                if (t.completed) "✓ انجام‌شده" else if (late) "معوق" else "باقی‌مانده",
                                fontSize = 12.sp,
                                color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
