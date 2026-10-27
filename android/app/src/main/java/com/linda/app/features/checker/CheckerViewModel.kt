package com.linda.app.features.checker

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linda.app.features.calls.CallCheck
import com.linda.app.features.calls.CallWarnings
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
    /** The last question, shown as the person's bubble above Linda's answer. */
    var asked by mutableStateOf<String?>(null)
        private set
    var result by mutableStateOf<ProcessResult?>(null)
        private set
    /** The answer when the question was a phone number (Ask Linda). */
    var numberAnswer by mutableStateOf<NumberAnswer?>(null)
        private set
    var checking by mutableStateOf(false)
        private set

    fun edit(newText: String) {
        text = newText
    }

    /** A message goes through the same path as a real SMS; a number is looked up like an incoming call. */
    fun check(body: String) {
        if (body.isBlank() || checking) return
        text = body
        asked = body.trim()
        result = null
        numberAnswer = null
        checking = true
        viewModelScope.launch {
            when (val input = AskInput.parse(body)) {
                is AskInput.Number -> numberAnswer = NumberAnswer(
                    // A longer wait than a ringing call (4 s, not 1.5 s): here the person is waiting for the answer.
                    input.msisdn, CallWarnings.lookup(getApplication(), input.msisdn, timeoutMs = 4_000),
                )
                is AskInput.Message -> result = MessageProcessor(getApplication()).process(
                    input.text, sender = null, receivedAt = System.currentTimeMillis(), source = "checker", notify = false,
                )
            }
            text = "" // like a chat: the box empties, the question moves up into a bubble
            checking = false
        }
    }
}

/** What Linda found about a number someone typed into Ask Linda. */
data class NumberAnswer(val msisdn: String, val check: CallCheck)
