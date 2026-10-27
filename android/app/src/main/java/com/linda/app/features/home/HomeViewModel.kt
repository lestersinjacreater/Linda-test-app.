package com.linda.app.features.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linda.app.LindaApp
import com.linda.app.core.util.startOfMonthMillis
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.linda.app.core.data.DetectionEntity

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as LindaApp).database.detectionDao()

    /** Number of scams caught since the 1st of this month. Updates live as new ones are saved. */
    val scamsCaughtThisMonth: StateFlow<Int> =
        dao.countScamsSince(startOfMonthMillis())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The three newest warnings the person has not marked safe, for Home's "Recent alerts". */
    val recent: StateFlow<List<DetectionEntity>> =
        dao.observeAll()
            .map { all -> all.filter { it.level != "SAFE" && !it.markedSafe }.take(3) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
