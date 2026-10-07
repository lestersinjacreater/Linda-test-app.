package com.linda.app.features.calls

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android asks this service about every call. Linda ALWAYS lets the call ring (never auto-rejects):
 * it only shows a warning first when the number is a known scammer. Needs the "call screening" role,
 * which the user grants from Settings.
 */
class LindaCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val allow = CallResponse.Builder().build() // default response: let it ring, no blocking, no silencing
        val outgoing = Build.VERSION.SDK_INT >= 29 && callDetails.callDirection == Call.Details.DIRECTION_OUTGOING
        if (outgoing) {
            respondToCall(callDetails, allow)
            return
        }
        val number = callDetails.handle?.schemeSpecificPart
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                CallWarnings.check(applicationContext, number)
            } finally {
                respondToCall(callDetails, allow)
            }
        }
    }
}
