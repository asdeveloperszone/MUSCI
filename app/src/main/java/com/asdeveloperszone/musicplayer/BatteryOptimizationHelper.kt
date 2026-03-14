package com.asdeveloperszone.musicplayer
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
object BatteryOptimizationHelper {
    fun isIgnoring(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    fun requestIfNeeded(activity: Activity) {
        if (isIgnoring(activity)) return
        val mfr = Build.MANUFACTURER.lowercase()
        val steps = when {
            mfr.contains("xiaomi")||mfr.contains("redmi") ->
                "1. Settings → Apps → Manage Apps\n2. Find MUSIC PLAYER\n3. Battery Saver → No Restrictions\n4. Security → Autostart → Enable MUSIC PLAYER"
            mfr.contains("oppo")||mfr.contains("realme") ->
                "1. Settings → Battery → App Management\n2. Find MUSIC PLAYER → Allow Background\n3. Settings → Apps → MUSIC PLAYER → Autostart → Enable"
            mfr.contains("vivo") ->
                "1. iManager → App Manager → Autostart\n2. Enable MUSIC PLAYER\n3. Settings → Battery → High Background Power → Add MUSIC PLAYER"
            mfr.contains("huawei")||mfr.contains("honor") ->
                "1. Settings → Apps → MUSIC PLAYER → Battery\n2. Disable Power-intensive prompt\n3. App launch → Manage Manually\n4. Enable Auto-launch + Run in background"
            mfr.contains("samsung") ->
                "1. Settings → Apps → MUSIC PLAYER\n2. Battery → Unrestricted\n3. Settings → Battery → Background Usage Limits\n4. Never Sleeping Apps → Add MUSIC PLAYER"
            else ->
                "1. Settings → Battery → Battery Optimization\n2. Find MUSIC PLAYER → Don't Optimize\n3. Allow Background Activity in App Settings"
        }
        AlertDialog.Builder(activity)
            .setTitle("⚡ Keep Music Playing")
            .setMessage("Your device may stop music in background.\n\nTo fix:\n\n$steps")
            .setPositiveButton("Open Battery Settings") { _, _ ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        activity.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${activity.packageName}")
                        })
                    }
                } catch (e: Exception) {
                    try { activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    catch (e2: Exception) { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                }
            }
            .setNegativeButton("Later", null)
            .show()
    }
    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                })
            }
        } catch (e: Exception) { }
    }
}
