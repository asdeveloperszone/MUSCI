package com.asdeveloperszone.musicplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class MusicWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateWidget(ctx, mgr, id) }
    }

    companion object {
        fun updateWidget(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_music)
            val f = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            // Open Now Playing on tap
            val openIntent = PendingIntent.getActivity(ctx, 0,
                Intent(ctx, NowPlayingActivity::class.java), f)

            // Play/Pause
            val playIntent = PendingIntent.getService(ctx, 1,
                Intent(ctx, MusicService::class.java).apply { action = MusicService.ACTION_PLAY_PAUSE }, f)

            // Next
            val nextIntent = PendingIntent.getService(ctx, 2,
                Intent(ctx, MusicService::class.java).apply { action = MusicService.ACTION_NEXT }, f)

            // Prev
            val prevIntent = PendingIntent.getService(ctx, 3,
                Intent(ctx, MusicService::class.java).apply { action = MusicService.ACTION_PREV }, f)

            views.setOnClickPendingIntent(R.id.widgetRoot, openIntent)
            views.setOnClickPendingIntent(R.id.widgetBtnPlay, playIntent)
            views.setOnClickPendingIntent(R.id.widgetBtnNext, nextIntent)
            views.setOnClickPendingIntent(R.id.widgetBtnPrev, prevIntent)

            // Try to get current song info from prefs
            val prefs = ctx.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val title  = prefs.getString("title",  "No song playing") ?: "No song playing"
            val artist = prefs.getString("artist", "Tap to open") ?: "Tap to open"
            val playing = prefs.getBoolean("playing", false)

            views.setTextViewText(R.id.widgetTitle, title)
            views.setTextViewText(R.id.widgetArtist, artist)
            views.setImageViewResource(R.id.widgetBtnPlay,
                if (playing) R.drawable.ic_pause else R.drawable.ic_play)

            mgr.updateAppWidget(id, views)
        }

        fun updateWidgetData(ctx: Context, title: String, artist: String, playing: Boolean) {
            ctx.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).edit()
                .putString("title", title)
                .putString("artist", artist)
                .putBoolean("playing", playing)
                .apply()

            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(ctx, MusicWidget::class.java))
            ids.forEach { updateWidget(ctx, mgr, it) }
        }
    }
}
