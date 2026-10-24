package com.linda.app.features.permissions

import com.linda.app.features.permissions.SmsAccess.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SmsAccessTest {
    @Test fun olderPhonesOnlyNeedThePermissionScreen() {
        for (sdk in listOf(26, 29, 32)) {
            assertEquals(listOf(Step.OPEN_APP_INFO, Step.PERMISSIONS_SMS, Step.COME_BACK), SmsAccess.steps(sdk))
            assertFalse(Step.ALLOW_RESTRICTED in SmsAccess.steps(sdk))
        }
    }

    @Test fun android13AndNewerUnlockRestrictedSettingsBeforeThePermission() {
        for (sdk in listOf(33, 34, 35, 36)) {
            assertEquals(
                listOf(Step.OPEN_APP_INFO, Step.ALLOW_RESTRICTED, Step.PERMISSIONS_SMS, Step.COME_BACK),
                SmsAccess.steps(sdk),
            )
        }
    }
}
