package com.asdeveloperszone.musicplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_ON) {
            // Check if music is playing via prefs (service may not be bound)
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val playing = prefs.getBoolean("playing", false)
            if (playing) {
                // Launch NowPlaying over lock screen
                context.startActivity(
                    Intent(context, NowPlayingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                )
            }
        }
    }
}
