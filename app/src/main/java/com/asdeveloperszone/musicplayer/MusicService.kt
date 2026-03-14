package com.asdeveloperszone.musicplayer

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.palette.graphics.Palette

class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "MusicPlayerChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_PAUSE = "ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "ACTION_NEXT"
        const val ACTION_PREV = "ACTION_PREV"
        const val ACTION_STOP = "ACTION_STOP"
        private const val TAG = "MusicService"
    }

    private val binder = MusicBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var originalList: List<Song> = emptyList()
    private var playList: List<Song> = emptyList()
    private var currentIndex: Int = 0
    private var isPlaying: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var notificationJob: Thread? = null
    var isShuffle: Boolean = false
    var repeatMode: RepeatMode = RepeatMode.OFF

    var onSongChangeListener: ((Song) -> Unit)? = null
    var onPlayStateChangeListener: ((Boolean) -> Unit)? = null
    var onShuffleChangeListener: ((Boolean) -> Unit)? = null
    var onRepeatChangeListener: ((RepeatMode) -> Unit)? = null

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
            ACTION_STOP -> stopMusic()
        }
        return START_STICKY
    }

    fun setSongList(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        originalList = songs
        playList = if (isShuffle) songs.shuffled() else songs.toList()
        currentIndex = startIndex.coerceIn(0, songs.size - 1)
        playCurrent()
    }

    fun togglePlayPause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) { it.pause(); isPlaying = false }
                else { it.start(); isPlaying = true }
                onPlayStateChangeListener?.invoke(isPlaying)
                getCurrentSong()?.let { song -> postNotification(song) }
            }
        } catch (e: Exception) { Log.e(TAG, "togglePlayPause: ${e.message}") }
    }

    fun playNext() {
        if (playList.isEmpty()) return
        currentIndex = (currentIndex + 1) % playList.size
        playCurrent()
    }

    fun playPrevious() {
        if (playList.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) playList.size - 1 else currentIndex - 1
        playCurrent()
    }

    fun toggleShuffle() {
        isShuffle = !isShuffle
        val currentSong = getCurrentSong()
        playList = if (isShuffle) originalList.shuffled() else originalList.toList()
        currentIndex = currentSong?.let { s ->
            playList.indexOfFirst { it.id == s.id }.takeIf { it >= 0 } ?: 0
        } ?: 0
        onShuffleChangeListener?.invoke(isShuffle)
    }

    fun cycleRepeat() {
        repeatMode = repeatMode.next()
        onRepeatChangeListener?.invoke(repeatMode)
    }

    fun stopMusic() {
        try {
            notificationJob?.interrupt()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        } catch (e: Exception) { Log.e(TAG, "stopMusic: ${e.message}") }
    }

    fun seekTo(position: Int) { try { mediaPlayer?.seekTo(position) } catch (e: Exception) { } }
    fun getCurrentPosition(): Int = try { mediaPlayer?.currentPosition ?: 0 } catch (e: Exception) { 0 }
    fun getDuration(): Int = try { mediaPlayer?.duration ?: 0 } catch (e: Exception) { 0 }
    fun isCurrentlyPlaying(): Boolean = try { mediaPlayer?.isPlaying ?: false } catch (e: Exception) { false }
    fun getCurrentSong(): Song? = if (playList.isNotEmpty() && currentIndex in playList.indices) playList[currentIndex] else null

    private fun onSongFinished() {
        isPlaying = false
        onPlayStateChangeListener?.invoke(false)
    }

    private fun playCurrent() {
        if (playList.isEmpty()) return
        val song = playList.getOrNull(currentIndex) ?: return
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA).build()
                )
                setDataSource(applicationContext, song.uri)
                prepare()
                start()
                setOnCompletionListener {
                    when (repeatMode) {
                        RepeatMode.REPEAT_ONE -> playCurrent()
                        RepeatMode.OFF -> {
                            if (currentIndex < playList.size - 1) playNext()
                            else onSongFinished()
                        }
                        RepeatMode.REPEAT_ALL -> playNext()
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    onSongFinished()
                    true
                }
            }
            isPlaying = true
            onSongChangeListener?.invoke(song)
            onPlayStateChangeListener?.invoke(true)
            // Show text notification immediately, then update with album art
            postNotification(song)
        } catch (e: Exception) {
            Log.e(TAG, "playCurrent: ${e.message}")
            onSongFinished()
        }
    }

    // Cancel previous job, always posts fresh notification for current song
    private fun postNotification(song: Song) {
        notificationJob?.interrupt()
        notificationJob = Thread {
            // Show text-only notification immediately
            mainHandler.post {
                try {
                    startForeground(NOTIFICATION_ID, buildNotification(song, null))
                } catch (e: Exception) { }
            }
            // Then load album art and update
            var bitmap: Bitmap? = null
            try {
                song.albumArtUri?.let { uri ->
                    contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                        bitmap = BitmapFactory.decodeFileDescriptor(fd.fileDescriptor)
                    }
                }
            } catch (e: Exception) { bitmap = null }

            if (!Thread.interrupted()) {
                mainHandler.post {
                    try {
                        val notification = buildNotification(song, bitmap)
                        startForeground(NOTIFICATION_ID, notification)
                        NotificationManagerCompat.from(this@MusicService)
                            .notify(NOTIFICATION_ID, notification)
                    } catch (e: Exception) { Log.e(TAG, "notify: ${e.message}") }
                }
            }
        }
        notificationJob!!.start()
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

    private fun buildNotification(song: Song, albumArt: Bitmap?): Notification {
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val playPauseIntent = PendingIntent.getService(this, 0,
            Intent(this, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE }, piFlags)
        val nextIntent = PendingIntent.getService(this, 1,
            Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }, piFlags)
        val prevIntent = PendingIntent.getService(this, 2,
            Intent(this, MusicService::class.java).apply { action = ACTION_PREV }, piFlags)
        val stopIntent = PendingIntent.getService(this, 3,
            Intent(this, MusicService::class.java).apply { action = ACTION_STOP }, piFlags)
        val openIntent = PendingIntent.getActivity(this, 0,
            Intent(this, NowPlayingActivity::class.java), piFlags)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText("${song.artist} • ${song.album}")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_skip_previous, "Prev", prevIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(R.drawable.ic_skip_next, "Next", nextIntent)
            .addAction(R.drawable.ic_close, "Stop", stopIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)

        albumArt?.let { bmp ->
            builder.setLargeIcon(bmp)
            try {
                Palette.from(bmp).generate { palette ->
                    palette?.dominantSwatch?.rgb?.let { color -> builder.setColor(color) }
                }
            } catch (e: Exception) { }
        }
        return builder.build()
    }

    override fun onDestroy() {
        try {
            notificationJob?.interrupt()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) { }
        super.onDestroy()
    }
}
