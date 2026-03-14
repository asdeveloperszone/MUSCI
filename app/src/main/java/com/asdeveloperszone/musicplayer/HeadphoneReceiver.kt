package com.asdeveloperszone.musicplayer
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
class HeadphoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            if (prefs.getBoolean("pause_on_unplug", true)) {
                context.startService(Intent(context, MusicService::class.java).apply { action = MusicService.ACTION_PLAY_PAUSE })
            }
        }
    }
}
