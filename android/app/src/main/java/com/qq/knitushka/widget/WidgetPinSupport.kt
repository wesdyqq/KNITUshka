package com.qq.knitushka.widget

/** Vivo's stock launcher can advertise pinning support but silently ignore requests. */
internal fun needsManualWidgetPin(manufacturer: String, launcherPackage: String?): Boolean =
    launcherPackage == "com.bbk.launcher2" ||
        (manufacturer.equals("vivo", ignoreCase = true) && launcherPackage == "com.android.launcher3")
