package com.linda.app.features.inbox

import android.app.Application
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linda.app.LindaApp
import com.linda.app.core.data.DetectionEntity
import com.linda.app.core.util.PhoneNumbers
import com.linda.app.features.detection.ReasonsJson
import com.linda.app.features.detection.RiskLevel
import com.linda.app.features.sms.ContactsLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** What the result list shows for one sender. [newestId] opens that message's warning screen. */
data class GroupUi(
    val sender: String?, val count: Int, val worst: RiskLevel,
    val newestId: Long, val newestDate: Long, val preview: String, val category: String,
)

sealed interface InboxState {
    object Idle : InboxState
    data class Scanning(val checked: Int, val total: Int, val flagged: Int) : InboxState
    data class Done(val groups: List<GroupUi>, val flaggedCount: Int, val scanned: Int, val seconds: Double, val stoppedEarly: Boolean) : InboxState
    object Failed : InboxState
}

/** Runs "Scan my inbox" off the main thread and reports progress. The scoring itself is in [InboxScanner]. */
class InboxViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LindaApp
    private val _state = MutableStateFlow<InboxState>(InboxState.Idle)
    val state: StateFlow<InboxState> = _state
    private val cancelRequested = AtomicBoolean(false)
    private var job: Job? = null

    fun cancel() = cancelRequested.set(true)

    fun start() {
        if (job?.isActive == true) return
        cancelRequested.set(false)
        _state.value = InboxState.Scanning(0, 0, 0)
        job = viewModelScope.launch(Dispatchers.Default) {
            try {
                _state.value = scan()
            } catch (e: SecurityException) {
                _state.value = InboxState.Failed // permission taken away mid-way
            } catch (e: Exception) {
                _state.value = InboxState.Failed
            }
        }
    }

    private suspend fun scan(): InboxState {
        val begun = System.nanoTime()
        val since = InboxWindow.startMillis(System.currentTimeMillis())
        val allowed = app.database.senderDao().allAllowed().toSet()
        val scanner = InboxScanner(app.detector, { ContactsLookup.isInContacts(app, it) }, { it in allowed })

        var scanned = 0
        val cursor = app.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()), "${Telephony.Sms.DATE} DESC",
        ) ?: return InboxState.Failed
        val flagged: List<Flagged> = cursor.use { c ->
            val total = c.count
            val rows = generateSequence { if (c.moveToNext()) RawSms(c.getString(0), c.getString(1) ?: "", c.getLong(2)) else null }
            scanner.scan(rows, isCancelled = { cancelRequested.get() }) { checked, found ->
                scanned = checked
                _state.value = InboxState.Scanning(checked, total, found)
            }
        }

        // Save what was found, once only: scanning twice must not duplicate History.
        val dao = app.database.detectionDao()
        val ids = HashMap<Flagged, Long>()
        for (f in flagged) {
            ids[f] = dao.findId(f.date, f.sender) ?: dao.insert(
                DetectionEntity(
                    sender = f.sender, senderMsisdn = PhoneNumbers.toMsisdn(f.sender), body = f.body, level = f.verdict.level.name,
                    score = f.verdict.score, category = f.verdict.category, fingerprint = f.verdict.fingerprint,
                    reasonsJson = ReasonsJson.encode(f.verdict.reasons), modelVersion = f.verdict.modelVersion,
                    receivedAt = f.date, source = "inbox",
                ),
            )
        }
        val groups = InboxGrouping.group(flagged).map { g ->
            GroupUi(g.sender, g.messages.size, g.worst, ids.getValue(g.newest), g.newest.date, g.newest.body, g.newest.verdict.category)
        }
        return InboxState.Done(groups, flagged.size, scanned, (System.nanoTime() - begun) / 1e9, cancelRequested.get())
    }
}
