package com.linda.app.features.history

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewRuleTest {
    @Test fun aScamIsHiddenInLists() = assertTrue(PreviewRule.hidden("SCAM", markedSafe = false, source = "sms"))
    @Test fun aCautionIsShown() = assertFalse(PreviewRule.hidden("CAUTION", markedSafe = false, source = "sms"))
    @Test fun aScamMarkedSafeIsShown() = assertFalse(PreviewRule.hidden("SCAM", markedSafe = true, source = "sms"))
    @Test fun textThePersonPastedIsShown() = assertFalse(PreviewRule.hidden("SCAM", markedSafe = false, source = "checker"))
}
