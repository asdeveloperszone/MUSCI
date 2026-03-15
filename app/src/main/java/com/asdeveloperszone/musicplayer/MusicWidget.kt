package com.asdeveloperszone.musicplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class MusicWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(ctx, mgr, it) }
    }

    companion object {

        private fun makeServiceIntent(ctx: Context, action: String) =
            Intent(ctx, MusicService::class.java).apply { this.action = action }

        fun updateWidget(ctx: Context, mgr: AppWidgetManager, id: Int) {
            try {
                val f = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

                val views = RemoteViews(ctx.packageName, R.layout.widget_music)

                // Tap whole widget → open NowPlaying
                views.setOnClickPendingIntent(R.id.widgetRoot,
                    PendingIntent.getActivity(ctx, 10,
                        Intent(ctx, NowPlayingActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }, f))

                // Buttons
                views.setOnClickPendingIntent(R.id.widgetBtnPrev,
                    PendingIntent.getService(ctx, 11, makeServiceIntent(ctx, MusicService.ACTION_PREV), f))
                views.setOnClickPendingIntent(R.id.widgetBtnPlay,
                    PendingIntent.getService(ctx, 12, makeServiceIntent(ctx, MusicService.ACTION_PLAY_PAUSE), f))
                views.setOnClickPendingIntent(R.id.widgetBtnNext,
                    PendingIntent.getService(ctx, 13, makeServiceIntent(ctx, MusicService.ACTION_NEXT), f))

                // Data from prefs
                val p = ctx.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                views.setTextViewText(R.id.widgetTitle,  p.getString("title",  "No song playing") ?: "No song playing")
                views.setTextViewText(R.id.widgetArtist, p.getString("artist", "Open Music Player")  ?: "Open Music Player")
                views.setImageViewResource(R.id.widgetBtnPlay,
                    if (p.getBoolean("playing", false)) R.drawable.ic_pause else R.drawable.ic_play)

                mgr.updateAppWidget(id, views)
            } catch (e: Exception) { }
        }

        fun push(ctx: Context, title: String, artist: String, playing: Boolean) {
            try {
                ctx.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).edit()
                    .putString("title", title).putString("artist", artist)
                    .putBoolean("playing", playing).apply()
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, MusicWidget::class.java))
                ids.forEach { updateWidget(ctx, mgr, it) }
            } catch (e: Exception) { }
        }
    }
}
