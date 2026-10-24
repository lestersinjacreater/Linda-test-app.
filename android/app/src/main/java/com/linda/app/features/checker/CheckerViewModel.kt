package com.linda.app.features.checker

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linda.app.features.sms.MessageProcessor
import com.linda.app.features.sms.ProcessResult
import kotlinx.coroutines.launch

/**
 * Holds the Check screen's text and verdict. A view model lives through rotation and a language change, while plain
 * `remember` is wiped when Android rebuilds the screen: turning the phone sideways used to empty the box and lose the
 * verdict (found by the live phone, 24 Oct). The check runs here too, so a rotation in the middle cannot cancel it.
 */
class CheckerViewModel(application: Application) : AndroidViewModel(application) {
    var text by mutableStateOf("")
        private set
    var result by mutableStateOf<ProcessResult?>(null)
        private set
    var checking by mutableStateOf(false)
        private set

    fun edit(newText: String) {
        text = newText
        result = null // a changed message needs a new check
    }

    fun check(body: String) {
        if (body.isBlank() || checking) return
        text = body
        checking = true
        viewModelScope.launch {
            result = MessageProcessor(getApplication()).process(
                body, sender = null, receivedAt = System.currentTimeMillis(), source = "checker", notify = false,
            )
            checking = false
        }
    }
}
