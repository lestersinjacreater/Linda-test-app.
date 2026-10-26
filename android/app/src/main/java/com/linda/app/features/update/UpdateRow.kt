package com.linda.app.features.update

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.linda.app.R
import com.linda.app.core.ui.components.ListGroup
import com.linda.app.core.ui.components.ListRow
import com.linda.app.core.ui.theme.LindaTheme
import kotlinx.coroutines.launch
import java.io.File

/** Where the update row is: what it says and what a tap does. */
private sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object CheckFailed : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate, val percent: Int) : UpdateState
    data class DownloadFailed(val update: AvailableUpdate) : UpdateState
    data class NeedsPermission(val update: AvailableUpdate, val apk: File) : UpdateState
    data class Ready(val update: AvailableUpdate, val apk: File) : UpdateState
}

/**
 * Settings' "Updates" group (docs/design-system.md 7.13): checks once when Settings opens, then one tap downloads the
 * newer build and opens Android's installer. Hackathon only (see [UpdateCheck]).
 */
@Composable
fun UpdateGroup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }

    fun check(force: Boolean) {
        state = UpdateState.Checking
        scope.launch {
            state = try {
                Updater.check(force)?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
            } catch (e: Exception) {
                UpdateState.CheckFailed
            }
        }
    }

    fun install(update: AvailableUpdate, apk: File) {
        if (Updater.canInstall(context)) {
            state = UpdateState.Ready(update, apk)
            Updater.install(context, apk)
        } else {
            // First time only: Android needs "Install unknown apps" switched on for Linda, then a second tap installs.
            state = UpdateState.NeedsPermission(update, apk)
            Updater.openInstallPermission(context)
        }
    }

    fun download(update: AvailableUpdate) {
        state = UpdateState.Downloading(update, 0)
        scope.launch {
            val apk = Updater.download(context, update) { state = UpdateState.Downloading(update, it) }
            if (apk == null) state = UpdateState.DownloadFailed(update) else install(update, apk)
        }
    }

    LaunchedEffect(Unit) { check(force = false) }

    val title = stringResource(R.string.update_title)
    ListGroup(title) {
        when (val s = state) {
            UpdateState.Checking -> ListRow(stringResource(R.string.update_checking))
            UpdateState.UpToDate -> ListRow(stringResource(R.string.update_none), onClick = { check(force = true) })
            UpdateState.CheckFailed -> ListRow(stringResource(R.string.update_check_failed), onClick = { check(force = true) })
            is UpdateState.Available -> ListRow(
                stringResource(R.string.update_available_title, s.update.build),
                subtitle = stringResource(R.string.update_available_body, UpdateCheck.megabytes(s.update.sizeBytes)),
                onClick = { download(s.update) },
                trailing = { UpdateWord() },
            )
            is UpdateState.Downloading -> ListRow(
                stringResource(R.string.update_available_title, s.update.build),
                subtitle = stringResource(R.string.update_downloading, s.percent),
            )
            is UpdateState.DownloadFailed -> ListRow(
                stringResource(R.string.update_available_title, s.update.build),
                subtitle = stringResource(R.string.update_download_failed), onClick = { download(s.update) },
            )
            is UpdateState.NeedsPermission -> ListRow(
                stringResource(R.string.update_available_title, s.update.build),
                subtitle = stringResource(R.string.update_allow), onClick = { install(s.update, s.apk) },
                trailing = { UpdateWord() },
            )
            is UpdateState.Ready -> ListRow(
                stringResource(R.string.update_available_title, s.update.build),
                subtitle = stringResource(R.string.update_ready), onClick = { install(s.update, s.apk) },
                trailing = { UpdateWord() },
            )
        }
    }
}

/** The green "Update" on the right of the row. */
@Composable
private fun UpdateWord() {
    Text(stringResource(R.string.update_action), style = MaterialTheme.typography.labelLarge, color = LindaTheme.colors.primary)
}
