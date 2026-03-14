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

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIfNeeded(activity: Activity) {
        if (isIgnoringBatteryOptimizations(activity)) return
        showDialog(activity)
    }

    private fun showDialog(activity: Activity) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val steps = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
                "1. Open Settings\n2. Apps → Manage Apps\n3. Find MUSIC PLAYER\n4. Battery Saver → No Restrictions\n5. Also go to Security → Autostart → Enable MUSIC PLAYER"

            manufacturer.contains("oppo") || manufacturer.contains("realme") ->
                "1. Open Settings\n2. Battery → App Management\n3. Find MUSIC PLAYER\n4. Set to Allow Background\n5. Settings → Apps → MUSIC PLAYER → Autostart → Enable"

            manufacturer.contains("vivo") ->
                "1. Open Settings\n2. Battery → High Background Power Consumption\n3. Add MUSIC PLAYER\n4. Also: iManager → App Manager → Autostart → Enable MUSIC PLAYER"

            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                "1. Open Settings\n2. Apps → MUSIC PLAYER\n3. Battery → Disable Power-intensive prompt\n4. Set App launch to Manage Manually\n5. Enable Auto-launch, Secondary launch, Run in background"

            manufacturer.contains("samsung") ->
                "1. Open Settings\n2. Apps → MUSIC PLAYER\n3. Battery → Unrestricted\n4. Also: Settings → Battery → Background Usage Limits → Never Sleeping Apps → Add MUSIC PLAYER"

            manufacturer.contains("oneplus") ->
                "1. Open Settings\n2. Battery → Battery Optimization\n3. Find MUSIC PLAYER → Don't Optimize\n4. Also: Settings → Apps → MUSIC PLAYER → Battery → Allow Background Activity"

            else ->
                "1. Open Settings\n2. Battery → Battery Optimization\n3. Find MUSIC PLAYER\n4. Select Don't Optimize\n5. Also allow Background Activity in App Settings"
        }

        AlertDialog.Builder(activity)
            .setTitle("⚡ Keep Music Playing")
            .setMessage("Your device may stop music in background.\n\nTo fix this:\n\n$steps")
            .setPositiveButton("Open Battery Settings") { _, _ ->
                // Try standard Android request first
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        activity.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${activity.packageName}")
                            }
                        )
                    }
                } catch (e: Exception) {
                    // Fallback to general battery settings
                    try {
                        activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (e2: Exception) {
                        activity.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            }
            .setNegativeButton("Later", null)
            .setCancelable(true)
            .show()
    }
}
