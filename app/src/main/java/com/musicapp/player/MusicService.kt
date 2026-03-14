package com.musicapp.player

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.palette.graphics.Palette

class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "MusicPlayerChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_PAUSE = "ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "ACTION_NEXT"
        const val ACTION_PREV = "ACTION_PREV"
        const val ACTION_STOP = "ACTION_STOP"
    }

    private val binder = MusicBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var originalList: List<Song> = emptyList()
    private var playList: List<Song> = emptyList()
    private var currentIndex: Int = 0
    private var isPlaying: Boolean = false
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
        originalList = songs
        playList = if (isShuffle) songs.shuffled() else songs.toList()
        currentIndex = startIndex
        playCurrent()
    }

    fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) { it.pause(); isPlaying = false }
            else { it.start(); isPlaying = true }
            onPlayStateChangeListener?.invoke(isPlaying)
            updateNotification()
        }
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
        currentIndex = currentSong?.let { s -> playList.indexOfFirst { it.id == s.id }.coerceAtLeast(0) } ?: 0
        onShuffleChangeListener?.invoke(isShuffle)
    }

    fun cycleRepeat() {
        repeatMode = repeatMode.next()
        onRepeatChangeListener?.invoke(repeatMode)
    }

    fun stopMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun seekTo(position: Int) { mediaPlayer?.seekTo(position) }
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun isCurrentlyPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
    fun getCurrentSong(): Song? = if (playList.isNotEmpty()) playList[currentIndex] else null

    private fun playCurrent() {
        if (playList.isEmpty()) return
        val song = playList[currentIndex]
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA).build())
            setDataSource(applicationContext, song.uri)
            prepare()
            start()
            setOnCompletionListener {
                when (repeatMode) {
                    RepeatMode.REPEAT_ONE -> playCurrent()
                    RepeatMode.OFF -> { if (currentIndex < playList.size - 1) playNext() else onSongFinished() }
                    RepeatMode.REPEAT_ALL -> playNext()
                }
            }
        }
        isPlaying = true
        onSongChangeListener?.invoke(song)
        onPlayStateChangeListener?.invoke(true)
        loadAlbumArtAndNotify(song)
    }
    private fun onSongFinished() { isPlaying = false; onPlayStateChangeListener?.invoke(false) }

    private fun loadAlbumArtAndNotify(song: Song) {
        Thread {
            var bitmap: Bitmap? = null
            try {
                song.albumArtUri?.let {
                    val fd = contentResolver.openFileDescriptor(it, "r")
                    bitmap = BitmapFactory.decodeFileDescriptor(fd?.fileDescriptor)
                    fd?.close()
                }
            } catch (e: Exception) { bitmap = null }
            startForeground(NOTIFICATION_ID, buildNotification(song, bitmap))
        }.start()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Music Player", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Music playback controls"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(song: Song, albumArt: Bitmap? = null): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val playPauseIntent = PendingIntent.getService(this, 0, Intent(this, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE }, flags)
        val nextIntent = PendingIntent.getService(this, 1, Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }, flags)
        val prevIntent = PendingIntent.getService(this, 2, Intent(this, MusicService::class.java).apply { action = ACTION_PREV }, flags)
        val stopIntent = PendingIntent.getService(this, 3, Intent(this, MusicService::class.java).apply { action = ACTION_STOP }, flags)
        val openIntent = PendingIntent.getActivity(this, 0, Intent(this, NowPlayingActivity::class.java), flags)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_skip_previous, "Previous", prevIntent)
            .addAction(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play, if (isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(R.drawable.ic_skip_next, "Next", nextIntent)
            .addAction(R.drawable.ic_close, "Stop", stopIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1, 2))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)

        albumArt?.let {
            builder.setLargeIcon(it)
            Palette.from(it).generate { palette ->
                palette?.dominantSwatch?.rgb?.let { color -> builder.setColor(color) }
            }
        }
        return builder.build()
    }

    private fun updateNotification() {
        val song = getCurrentSong() ?: return
        loadAlbumArtAndNotify(song)
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        super.onDestroy()
    }
}
