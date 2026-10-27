package com.rooznegar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val LightC = lightColorScheme(
    primary = Color(0xFF5146A8), onPrimary = Color.White,
    background = Color(0xFFFAFAFC), surface = Color.White,
    onBackground = Color(0xFF191A22), onSurface = Color(0xFF191A22),
    onSurfaceVariant = Color(0xFF777B89), outlineVariant = Color(0xFFECECF2)
)
private val DarkC = darkColorScheme(
    primary = Color(0xFFA99CFF), onPrimary = Color(0xFF111318),
    background = Color(0xFF111318), surface = Color(0xFF191C23),
    onBackground = Color(0xFFF3F3F7), onSurface = Color(0xFFF3F3F7),
    onSurfaceVariant = Color(0xFFA0A4B2), outlineVariant = Color(0xFF292D37)
)

private class PickTarget(val start: LocalDate, val onPick: (LocalDate) -> Unit)

@Composable
fun App(vm: MainViewModel) {
    val theme by vm.theme.collectAsState()
    val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) DarkC else LightC) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Screen(vm) }
        }
    }
}

@Composable
private fun Screen(vm: MainViewModel) {
    val today by vm.today.collectAsState()
    val sel by vm.selected.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val overdue by vm.overdue.collectAsState()
    val theme by vm.theme.collectAsState()
    var text by remember { mutableStateOf("") }
    var showOverdue by remember { mutableStateOf(true) }
    var picker by remember { mutableStateOf<PickTarget?>(null) }
    var toDelete by remember { mutableStateOf<TaskEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    val ctx = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { vm.refreshToday(); showOverdue = true }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    fun submit() {
        if (text.isBlank()) return
        vm.add(text, sel) { ok -> if (ok) { text = ""; error = null } else error = "ثبت کار انجام نشد." }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.cycleTheme() }) {
                Text(when (theme) { "light" -> "روشن"; "dark" -> "تاریک"; else -> "خودکار" })
            }
            Column(
                Modifier.weight(1f).clickable { picker = PickTarget(sel) { vm.select(it) } },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(jalaliFull(sel), fontSize = 26.sp, style = MaterialTheme.typography.titleLarge)
                Text(gregorian(sel), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { picker = PickTarget(sel) { vm.select(it) } }) { Text("تقویم") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { vm.select(sel.minusDays(1)) }) { Text("روز قبل") }
            if (sel != today) TextButton(onClick = { vm.select(today) }) { Text("امروز") }
            TextButton(onClick = { vm.select(sel.plusDays(1)) }) { Text("روز بعد") }
        }
        if (overdue.isNotEmpty() && !showOverdue) {
            TextButton(onClick = { showOverdue = true }, Modifier.fillMaxWidth()) {
                Text("${faNum(overdue.size)} کار نیازمند تعیین تکلیف")
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(200) },
                placeholder = { Text("افزودن کار جدید...") }, singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() })
            )
            Button(onClick = { submit() }, Modifier.padding(start = 8.dp)) { Text("+") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        Text(
            (if (sel == today) "کارهای امروز" else "کارهای این روز") + " (${faNum(tasks.size)})",
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)
        )
        if (tasks.isEmpty()) {
            Text(
                "برای این روز کاری ثبت نشده است.", Modifier.fillMaxWidth().padding(24.dp),
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(tasks, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = t.completed, onCheckedChange = { vm.setDone(t.id, it) })
                    Column(Modifier.weight(1f).clickable { editing = t }) {
                        Text(
                            (if (t.priority) "🚩 " else "") + t.title,
                            textDecoration = if (t.completed) TextDecoration.LineThrough else null,
                            color = if (t.completed) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (t.originalDate != t.scheduledDate) {
                            Text(
                                "از ${jalaliDayMonth(LocalDate.parse(t.originalDate))}", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val rem = t.reminderAt
                        if (rem != null && !t.completed) {
                            val lt = Instant.ofEpochMilli(rem).atZone(TEHRAN).toLocalTime()
                            Text(
                                "⏰ " + faTime(lt.hour, lt.minute), fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = { toDelete = t }) { Text("×") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    toDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("حذف کار") }, text = { Text(t.title) },
            confirmButton = { TextButton(onClick = { vm.delete(t.id); toDelete = null }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("انصراف") } }
        )
    }

    if (overdue.isNotEmpty() && showOverdue) {
        Dialog(onDismissRequest = { showOverdue = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp)) {
                    Text("${faNum(overdue.size)} کار باقی مانده است", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "برای هر کار تاریخ جدید را خودتان تعیین کنید.", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(overdue, key = { it.id }) { t ->
                            val late = ChronoUnit.DAYS.between(LocalDate.parse(t.scheduledDate), today).toInt()
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Text((if (t.priority) "🚩 " else "") + t.title)
                                Text(
                                    "از ${jalaliDayMonth(LocalDate.parse(t.scheduledDate))} · ${faNum(late)} روز تأخیر",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { vm.move(t, today) }, Modifier.weight(1f)) { Text("امروز", fontSize = 13.sp) }
                                    OutlinedButton(onClick = { vm.move(t, today.plusDays(1)) }, Modifier.weight(1f)) { Text("فردا", fontSize = 13.sp) }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = { picker = PickTarget(today) { d -> vm.move(t, d) } },
                                        Modifier.weight(1f)
                                    ) { Text("انتخاب تاریخ", fontSize = 13.sp) }
                                    TextButton(onClick = { vm.snooze(t.id) }, Modifier.weight(1f)) { Text("فعلاً نه", fontSize = 13.sp) }
                                }
                            }
                        }
                    }
                    TextButton(onClick = { showOverdue = false }) { Text("بستن") }
                }
            }
        }
    }

    editing?.let { t ->
        EditDialog(
            t = t, today = today, onDismiss = { editing = null },
            onSave = { title, prio, date, reminder ->
                if (reminder != null && Build.VERSION.SDK_INT >= 33 &&
                    ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                vm.update(t.id, title, prio, date, reminder) { ok ->
                    if (ok) editing = null else error = "ذخیره انجام نشد."
                }
            }
        )
    }

    picker?.let { p ->
        JalaliPicker(
            start = p.start, today = today,
            onPick = { d -> picker = null; p.onPick(d) },
            onDismiss = { picker = null }
        )
    }
}

@Composable
private fun JalaliPicker(start: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    var anchor by remember { mutableStateOf(start) }
    val days = remember(anchor) { jalaliMonthDays(anchor) }
    val offset = (days.first().dayOfWeek.value + 1) % 7 // Saturday = column 0
    val cells: List<LocalDate?> = List(offset) { null } + days
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { anchor = days.first().minusDays(1) }) { Text("›") }
                    Text(
                        jalaliMonthYear(days.first()), Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = { anchor = days.last().plusDays(1) }) { Text("‹") }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach {
                        Text(
                            it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0 until 7) {
                            val d = week.getOrNull(i)
                            if (d == null) {
                                Spacer(Modifier.weight(1f).height(42.dp))
                            } else {
                                val isStart = d == start
                                Box(
                                    Modifier.weight(1f).height(42.dp).padding(2.dp).clip(CircleShape)
                                        .background(if (isStart) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .then(
                                            if (d == today) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                            else Modifier
                                        )
                                        .clickable { onPick(d) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        jalaliDay(d), fontSize = 14.sp,
                                        color = if (isStart) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss) { Text("بستن") }
            }
        }
    }
}


@Composable
private fun EditDialog(
    t: TaskEntity, today: LocalDate, onDismiss: () -> Unit,
    onSave: (String, Boolean, LocalDate, Long?) -> Unit
) {
    var title by remember { mutableStateOf(t.title) }
    var prio by remember { mutableStateOf(t.priority) }
    var date by remember { mutableStateOf(LocalDate.parse(t.scheduledDate)) }
    val old = t.reminderAt?.let { Instant.ofEpochMilli(it).atZone(TEHRAN).toLocalTime() }
    var remOn by remember { mutableStateOf(t.reminderAt != null) }
    var hour by remember { mutableStateOf(old?.hour ?: 9) }
    var minute by remember { mutableStateOf(old?.minute ?: 0) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    fun save() {
        if (title.isBlank()) { err = "عنوان نمی‌تواند خالی باشد."; return }
        val reminder = if (remOn) date.atTime(hour, minute).atZone(TEHRAN).toInstant().toEpochMilli() else null
        if (reminder != null && reminder != t.reminderAt && reminder <= System.currentTimeMillis()) {
            err = "زمان یادآور گذشته است."; return
        }
        onSave(title, prio, date, reminder)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("ویرایش کار", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(200); err = null },
                    label = { Text("عنوان") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🚩 مهم (بالاتر نمایش داده شود)", Modifier.weight(1f))
                    Switch(checked = prio, onCheckedChange = { prio = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("تاریخ: ${jalaliFull(date)}", Modifier.weight(1f))
                    TextButton(onClick = { pickDate = true }) { Text("تغییر") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("⏰ یادآور", Modifier.weight(1f))
                    Switch(checked = remOn, onCheckedChange = { remOn = it; err = null })
                }
                if (remOn) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("ساعت ${faTime(hour, minute)}", Modifier.weight(1f))
                        TextButton(onClick = { pickTime = true }) { Text("تغییر") }
                    }
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("انصراف") }
                    Button(onClick = { save() }) { Text("ذخیره") }
                }
            }
        }
    }
    if (pickDate) {
        JalaliPicker(start = date, today = today, onPick = { date = it; pickDate = false; err = null }, onDismiss = { pickDate = false })
    }
    if (pickTime) {
        TimeDialog(hour, minute, onOk = { h, m -> hour = h; minute = m; pickTime = false; err = null }, onDismiss = { pickTime = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(h: Int, m: Int, onOk: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val st = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { TimePicker(state = st) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("انصراف") }
                    TextButton(onClick = { onOk(st.hour, st.minute) }) { Text("تأیید") }
                }
            }
        }
    }
}
