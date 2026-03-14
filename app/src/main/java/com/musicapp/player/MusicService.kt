package com.musicapp.player

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "MusicPlayerChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_PAUSE = "ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "ACTION_NEXT"
        const val ACTION_PREV = "ACTION_PREV"
    }

    private val binder = MusicBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var songList: List<Song> = emptyList()
    private var currentIndex: Int = 0
    private var isPlaying: Boolean = false
    private var onSongChangeListener: ((Song) -> Unit)? = null
    private var onPlayStateChangeListener: ((Boolean) -> Unit)? = null

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> playNext()
            ACTION_PREV -> playPrevious()
        }
        return START_STICKY
    }

    fun setSongList(songs: List<Song>, startIndex: Int = 0) {
        songList = songs
        currentIndex = startIndex
        playCurrent()
    }

    fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
            } else {
                it.start()
                isPlaying = true
            }
            onPlayStateChangeListener?.invoke(isPlaying)
            updateNotification()
        }
    }

    fun playNext() {
        if (songList.isEmpty()) return
        currentIndex = (currentIndex + 1) % songList.size
        playCurrent()
    }

    fun playPrevious() {
        if (songList.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) songList.size - 1 else currentIndex - 1
        playCurrent()
    }

    fun seekTo(position: Int) { mediaPlayer?.seekTo(position) }
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
    fun getCurrentSong(): Song? = if (songList.isNotEmpty()) songList[currentIndex] else null

    fun setOnSongChangeListener(listener: (Song) -> Unit) { onSongChangeListener = listener }
    fun setOnPlayStateChangeListener(listener: (Boolean) -> Unit) { onPlayStateChangeListener = listener }

    private fun playCurrent() {
        if (songList.isEmpty()) return
        val song = songList[currentIndex]
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(applicationContext, song.uri)
            prepare()
            start()
            setOnCompletionListener { playNext() }
        }
        isPlaying = true
        onSongChangeListener?.invoke(song)
        onPlayStateChangeListener?.invoke(true)
        startForeground(NOTIFICATION_ID, buildNotification(song))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Music Player", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(song: Song): Notification {
        val playPauseIntent = PendingIntent.getService(this, 0,
            Intent(this, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val nextIntent = PendingIntent.getService(this, 1,
            Intent(this, MusicService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val prevIntent = PendingIntent.getService(this, 2,
            Intent(this, MusicService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val openAppIntent = PendingIntent.getActivity(this, 0,
            Intent(this, NowPlayingActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(openAppIntent)
            .addAction(R.drawable.ic_skip_previous, "Previous", prevIntent)
            .addAction(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(R.drawable.ic_skip_next, "Next", nextIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification() {
        val song = getCurrentSong() ?: return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(song))
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}
