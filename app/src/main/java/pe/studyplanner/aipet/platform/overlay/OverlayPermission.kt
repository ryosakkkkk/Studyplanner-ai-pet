package pe.studyplanner.aipet.platform.overlay

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings

/** Query only: never grants a permission or attaches a probe window. */
object OverlayPermission {
    @Suppress("DEPRECATION")
    fun allowed(context: Context): Boolean = try {
        // API 26 may return a stale result after the Settings round trip. Ask
        // AppOps for this application's own UID/package and accept only ALLOWED.
        if (Build.VERSION.SDK_INT == 26) {
            context.getSystemService(AppOpsManager::class.java).checkOpNoThrow(
                AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, Process.myUid(), context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        } else Settings.canDrawOverlays(context)
    } catch (_: RuntimeException) { false }
}
