package com.asdeveloperszone.musicplayer

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat
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
        const val ACTION_REWIND = "ACTION_REWIND"
        const val ACTION_FORWARD = "ACTION_FORWARD"
        const val SEEK_AMOUNT_MS = 10000 // 10 seconds
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
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var mediaSession: MediaSessionCompat? = null

    var isShuffle: Boolean = false
    var repeatMode: RepeatMode = RepeatMode.OFF

    var onSongChangeListener: ((Song) -> Unit)? = null
    var onPlayStateChangeListener: ((Boolean) -> Unit)? = null
    var onShuffleChangeListener: ((Boolean) -> Unit)? = null
    var onRepeatChangeListener: ((RepeatMode) -> Unit)? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> { pausePlayback(); hasAudioFocus = false }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pausePlayback()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mediaPlayer?.setVolume(0.3f, 0.3f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1f, 1f)
                if (!isPlaying && hasAudioFocus) resumePlayback()
            }
        }
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        setupMediaSession()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicPlayer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { resumePlayback() }
                override fun onPause() { pausePlayback() }
                override fun onSkipToNext() { playNext() }
                override fun onSkipToPrevious() { playPrevious() }
                override fun onStop() { stopMusic() }
                override fun onSeekTo(pos: Long) { seekTo(pos.toInt()) }
                override fun onFastForward() { forward() }
                override fun onRewind() { rewind() }
            })
            isActive = true
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> playNext()
            ACTION_PREV -> playPrevious()
            ACTION_STOP -> stopMusic()
            ACTION_REWIND -> rewind()
            ACTION_FORWARD -> forward()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        getCurrentSong()?.let { postNotification(it) }
    }

    private fun requestAudioFocus(): Boolean {
        audioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            audioManager!!.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager!!.requestAudioFocus(audioFocusChangeListener,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

    fun setSongList(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        originalList = songs
        playList = if (isShuffle) songs.shuffled() else songs.toList()
        currentIndex = startIndex.coerceIn(0, songs.size - 1)
        playCurrent()
    }

    fun togglePlayPause() {
        if (isPlaying) pausePlayback() else resumePlayback()
    }

    fun pausePlayback() {
        try {
            mediaPlayer?.pause()
            isPlaying = false
            onPlayStateChangeListener?.invoke(false)
            updateMediaSessionState()
            getCurrentSong()?.let { postNotification(it) }
        } catch (e: Exception) { Log.e(TAG, "pause: ${e.message}") }
    }

    fun resumePlayback() {
        try {
            if (requestAudioFocus()) {
                mediaPlayer?.start()
                isPlaying = true
                hasAudioFocus = true
                onPlayStateChangeListener?.invoke(true)
                updateMediaSessionState()
                getCurrentSong()?.let { postNotification(it) }
            }
        } catch (e: Exception) { Log.e(TAG, "resume: ${e.message}") }
    }

    fun playNext() {
        if (playList.isEmpty()) return
        currentIndex = (currentIndex + 1) % playList.size
        playCurrent()
    }

    fun playPrevious() {
        if (playList.isEmpty()) return
        // If more than 3 seconds in, restart current song
        if ((mediaPlayer?.currentPosition ?: 0) > 3000) {
            seekTo(0)
            return
        }
        currentIndex = if (currentIndex - 1 < 0) playList.size - 1 else currentIndex - 1
        playCurrent()
    }

    fun rewind() {
        try {
            val newPos = ((mediaPlayer?.currentPosition ?: 0) - SEEK_AMOUNT_MS).coerceAtLeast(0)
            seekTo(newPos)
        } catch (e: Exception) { }
    }

    fun forward() {
        try {
            val duration = mediaPlayer?.duration ?: 0
            val newPos = ((mediaPlayer?.currentPosition ?: 0) + SEEK_AMOUNT_MS).coerceAtMost(duration)
            seekTo(newPos)
        } catch (e: Exception) { }
    }

    fun toggleShuffle() {
        isShuffle = !isShuffle
        val currentSong = getCurrentSong()
        playList = if (isShuffle) originalList.shuffled() else originalList.toList()
        currentIndex = currentSong?.let { s ->
            playList.indexOfFirst { it.id == s.id }.takeIf { it >= 0 } ?: 0 } ?: 0
        onShuffleChangeListener?.invoke(isShuffle)
    }

    fun cycleRepeat() {
        repeatMode = repeatMode.next()
        onRepeatChangeListener?.invoke(repeatMode)
    }

    fun stopMusic() {
        try {
            abandonAudioFocus()
            notificationJob?.interrupt()
            mediaSession?.isActive = false
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        } catch (e: Exception) { Log.e(TAG, "stopMusic: ${e.message}") }
    }

    fun seekTo(position: Int) {
        try {
            mediaPlayer?.seekTo(position)
            updateMediaSessionState()
        } catch (e: Exception) { }
    }

    fun getCurrentPosition(): Int = try { mediaPlayer?.currentPosition ?: 0 } catch (e: Exception) { 0 }
    fun getDuration(): Int = try { mediaPlayer?.duration ?: 0 } catch (e: Exception) { 0 }
    fun isCurrentlyPlaying(): Boolean = try { mediaPlayer?.isPlaying ?: false } catch (e: Exception) { false }
    fun getCurrentSong(): Song? = if (playList.isNotEmpty() && currentIndex in playList.indices) playList[currentIndex] else null

    private fun onSongFinished() {
        isPlaying = false
        onPlayStateChangeListener?.invoke(false)
        getCurrentSong()?.let { postNotification(it) }
    }

    private fun playCurrent() {
        if (playList.isEmpty()) return
        val song = playList.getOrNull(currentIndex) ?: return
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            if (!requestAudioFocus()) return
            hasAudioFocus = true

            mediaPlayer = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build())
                setDataSource(applicationContext, song.uri)
                prepare()
                start()
                setOnCompletionListener {
                    when (repeatMode) {
                        RepeatMode.REPEAT_ONE -> playCurrent()
                        RepeatMode.OFF -> if (currentIndex < playList.size - 1) playNext() else onSongFinished()
                        RepeatMode.REPEAT_ALL -> playNext()
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    onSongFinished(); true
                }
            }
            isPlaying = true
            onSongChangeListener?.invoke(song)
            onPlayStateChangeListener?.invoke(true)
            updateMediaSessionMetadata(song)
            updateMediaSessionState()
            postNotification(song)
        } catch (e: Exception) {
            Log.e(TAG, "playCurrent: ${e.message}")
            onSongFinished()
        }
    }

    private fun updateMediaSessionMetadata(song: Song) {
        try {
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
                .build()
            mediaSession?.setMetadata(metadata)
        } catch (e: Exception) { }
    }

    private fun updateMediaSessionState() {
        try {
            val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_FAST_FORWARD or
                    PlaybackStateCompat.ACTION_REWIND or
                    PlaybackStateCompat.ACTION_STOP
                )
                .setState(state, getCurrentPosition().toLong(), 1f)
                .build()
            mediaSession?.setPlaybackState(playbackState)
        } catch (e: Exception) { }
    }

    private fun postNotification(song: Song) {
        notificationJob?.interrupt()
        notificationJob = Thread {
            mainHandler.post {
                try { startForeground(NOTIFICATION_ID, buildNotification(song, null)) } catch (e: Exception) { }
            }
            var bitmap: Bitmap? = null
            try {
                song.albumArtUri?.let { uri ->
                    contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                        bitmap = BitmapFactory.decodeFileDescriptor(fd.fileDescriptor,
                            null, BitmapFactory.Options().apply { inSampleSize = 2 })
                    }
                }
            } catch (e: Exception) { bitmap = null }

            if (Thread.interrupted()) return@Thread

            val finalBitmap = bitmap
            mainHandler.post {
                try {
                    // Update media session art
                    finalBitmap?.let { bmp ->
                        val meta = MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
                            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bmp)
                            .build()
                        mediaSession?.setMetadata(meta)
                    }
                    val notification = buildNotification(song, finalBitmap)
                    startForeground(NOTIFICATION_ID, notification)
                    try {
                        NotificationManagerCompat.from(this@MusicService).notify(NOTIFICATION_ID, notification)
                    } catch (e: SecurityException) { }
                } catch (e: Exception) { Log.e(TAG, "notify: ${e.message}") }
            }
        }
        notificationJob!!.start()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Music Player", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Music playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(song: Song, albumArt: Bitmap?): Notification {
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val playPauseIntent = PendingIntent.getService(this, 0, Intent(this, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE }, piFlags)
        val nextIntent = PendingIntent.getService(this, 1, Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }, piFlags)
        val prevIntent = PendingIntent.getService(this, 2, Intent(this, MusicService::class.java).apply { action = ACTION_PREV }, piFlags)
        val stopIntent = PendingIntent.getService(this, 3, Intent(this, MusicService::class.java).apply { action = ACTION_STOP }, piFlags)
        val rewindIntent = PendingIntent.getService(this, 4, Intent(this, MusicService::class.java).apply { action = ACTION_REWIND }, piFlags)
        val forwardIntent = PendingIntent.getService(this, 5, Intent(this, MusicService::class.java).apply { action = ACTION_FORWARD }, piFlags)
        val openIntent = PendingIntent.getActivity(this, 0, Intent(this, NowPlayingActivity::class.java), piFlags)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText("${song.artist} • ${song.album}")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_skip_previous, "Prev", prevIntent)
            .addAction(R.drawable.ic_rewind, "-10s", rewindIntent)
            .addAction(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(R.drawable.ic_forward, "+10s", forwardIntent)
            .addAction(R.drawable.ic_skip_next, "Next", nextIntent)
            .addAction(R.drawable.ic_close, "Stop", stopIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 2, 4))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setAutoCancel(false)

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
            abandonAudioFocus()
            notificationJob?.interrupt()
            mediaSession?.release()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) { }
        super.onDestroy()
    }
}
