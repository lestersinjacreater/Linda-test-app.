package com.linda.app.features.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linda.app.LindaApp
import com.linda.app.core.util.startOfMonthMillis
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as LindaApp).database.detectionDao()

    /** Number of scams caught since the 1st of this month. Updates live as new ones are saved. */
    val scamsCaughtThisMonth: StateFlow<Int> =
        dao.countScamsSince(startOfMonthMillis())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
