package com.linda.app.features.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linda.app.LindaApp
import com.linda.app.core.data.DetectionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as LindaApp).database.detectionDao()

    /** "ALL", "SCAM" or "CAUTION". */
    val levelFilter = MutableStateFlow("ALL")
    val query = MutableStateFlow("")

    /** Flagged messages, newest first, narrowed by the level filter and the search text. */
    val items: StateFlow<List<DetectionEntity>> =
        combine(dao.observeAll(), levelFilter, query) { all, level, text ->
            all.filter { (level == "ALL" || it.level == level) }
                .filter { text.isBlank() || it.body.contains(text, ignoreCase = true) || (it.sender ?: "").contains(text, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
