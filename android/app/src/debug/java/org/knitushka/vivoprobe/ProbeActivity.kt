package org.knitushka.vivoprobe

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import com.qq.knitushka.MainActivity

/** Retains the original probe entry point and refreshes only the research provider. */
class ProbeActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val ids = AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, ProbeProvider::class.java))
        sendBroadcast(Intent(this, ProbeProvider::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        if (intent.getBooleanExtra("pinHybrid", false)) {
            val manager = AppWidgetManager.getInstance(this)
            if (android.os.Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported) {
                manager.requestPinAppWidget(ComponentName(this, ProbeProvider::class.java), null, null)
            }
            finish()
            return
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
