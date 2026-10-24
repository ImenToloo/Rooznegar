package com.rooznegar.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TaskRepository(AppDb.get(app))
    private val prefs = app.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    val today = MutableStateFlow(todayTehran())
    val selected = MutableStateFlow(todayTehran())
    val theme = MutableStateFlow(prefs.getString("theme", "auto") ?: "auto")

    val tasks: StateFlow<List<TaskEntity>> = selected
        .flatMapLatest { repo.day(it.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val overdue: StateFlow<List<TaskEntity>> = today
        .flatMapLatest { repo.overdue(it.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            while (true) {
                refreshToday()
                delay(30_000)
            }
        }
    }

    fun refreshToday() {
        val now = todayTehran()
        if (now != today.value) {
            val followToday = selected.value == today.value
            today.value = now
            if (followToday) selected.value = now
        }
    }

    fun select(d: LocalDate) { selected.value = d }

    fun cycleTheme() {
        val next = when (theme.value) { "auto" -> "light"; "light" -> "dark"; else -> "auto" }
        theme.value = next
        prefs.edit().putString("theme", next).apply()
    }

    fun add(title: String, date: LocalDate, cb: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching { repo.add(title, date.toString()) }.getOrDefault(false)
            cb(ok)
        }
    }

    fun setDone(id: String, done: Boolean) { viewModelScope.launch { runCatching { repo.setDone(id, done) } } }
    fun delete(id: String) { viewModelScope.launch { runCatching { repo.delete(id) } } }
    fun snooze(id: String) { viewModelScope.launch { runCatching { repo.snooze(id, today.value.toString()) } } }

    fun move(t: TaskEntity, to: LocalDate) {
        viewModelScope.launch {
            runCatching { repo.move(t.id, t.scheduledDate, to.toString(), "user_rollover") }
        }
    }
}
