package com.qq.knitushka.widget

import org.junit.Assert.*
import org.junit.Test

class WidgetPinSupportTest {
    @Test fun `stock vivo launchers use manual adding`() {
        assertTrue(needsManualWidgetPin("vivo", "com.bbk.launcher2"))
        assertTrue(needsManualWidgetPin("vivo", "com.android.launcher3"))
    }

    @Test fun `third party launchers on vivo keep native pinning`() {
        assertFalse(needsManualWidgetPin("vivo", "com.teslacoilsw.launcher"))
        assertFalse(needsManualWidgetPin("Google", "com.android.launcher3"))
        assertFalse(needsManualWidgetPin("vivo", null))
    }
}
