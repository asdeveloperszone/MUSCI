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
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
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
        const val SEEK_AMOUNT_MS = 10000
        private const val TAG = "MusicService"
    }

    private val binder = MusicBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var originalList: List<Song> = emptyList()
    private var playList: MutableList<Song> = mutableListOf()
    private var currentIndex: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var notificationThread: Thread? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var mediaSession: MediaSessionCompat? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Single source of truth: always read from mediaPlayer directly
    val isPlaying: Boolean get() = mediaPlayer?.isPlaying == true

    var isShuffle: Boolean = false
    var repeatMode: RepeatMode = RepeatMode.OFF

    // Listeners — always invoked on main thread
    var onSongChangeListener: ((Song) -> Unit)? = null
    var onPlayStateChangeListener: ((Boolean) -> Unit)? = null
    var onShuffleChangeListener: ((Boolean) -> Unit)? = null
    var onRepeatChangeListener: ((RepeatMode) -> Unit)? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        mainHandler.post {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    hasAudioFocus = false
                    pauseInternal()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseInternal()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    mediaPlayer?.setVolume(0.2f, 0.2f)
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    mediaPlayer?.setVolume(1f, 1f)
                }
            }
        }
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        acquireWakeLock()
        createNotificationChannel()
        setupMediaSession()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MusicPlayer::WakeLock"
        ).apply { acquire(10 * 60 * 1000L) } // 10 min max
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicPlayer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { togglePlayPause() }
                override fun onPause() { togglePlayPause() }
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
            ACTION_NEXT       -> playNext()
            ACTION_PREV       -> playPrevious()
            ACTION_STOP       -> stopMusic()
            ACTION_REWIND     -> rewind()
            ACTION_FORWARD    -> forward()
        }
        return START_STICKY
    }

    // Keep alive when swiped from recents
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Re-post notification so foreground service stays alive
        getCurrentSong()?.let {
            startForeground(NOTIFICATION_ID, buildNotification(it, null))
        }
    }

    // ─── Audio Focus ──────────────────────────────────────────────────────────

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener, mainHandler)
                .build()
            audioFocusRequest = req
            audioManager?.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) { }
        hasAudioFocus = false
    }

    // ─── Playback Controls ────────────────────────────────────────────────────

    fun setSongList(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        originalList = songs.toList()
        playList = (if (isShuffle) songs.shuffled() else songs).toMutableList()
        currentIndex = startIndex.coerceIn(0, playList.size - 1)
        playCurrent()
    }

    fun togglePlayPause() {
        if (isPlaying) pauseInternal() else resumeInternal()
    }

    private fun pauseInternal() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                notifyPlayState(false)
                getCurrentSong()?.let { postNotification(it) }
                updateSessionState()
            }
        } catch (e: Exception) { Log.e(TAG, "pause: ${e.message}") }
    }

    private fun resumeInternal() {
        try {
            if (mediaPlayer != null && mediaPlayer?.isPlaying == false) {
                if (!requestAudioFocus()) return
                hasAudioFocus = true
                mediaPlayer?.start()
                notifyPlayState(true)
                getCurrentSong()?.let { postNotification(it) }
                updateSessionState()
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
        if ((mediaPlayer?.currentPosition ?: 0) > 3000) {
            seekTo(0)
            return
        }
        currentIndex = if (currentIndex <= 0) playList.size - 1 else currentIndex - 1
        playCurrent()
    }

    fun rewind() {
        val newPos = ((mediaPlayer?.currentPosition ?: 0) - SEEK_AMOUNT_MS).coerceAtLeast(0)
        seekTo(newPos)
    }

    fun forward() {
        val dur = mediaPlayer?.duration ?: 0
        val newPos = ((mediaPlayer?.currentPosition ?: 0) + SEEK_AMOUNT_MS).coerceAtMost(dur)
        seekTo(newPos)
    }

    fun seekTo(position: Int) {
        try {
            mediaPlayer?.seekTo(position)
            updateSessionState()
        } catch (e: Exception) { }
    }

    fun toggleShuffle() {
        isShuffle = !isShuffle
        val cur = getCurrentSong()
        playList = (if (isShuffle) originalList.shuffled() else originalList).toMutableList()
        currentIndex = cur?.let { s -> playList.indexOfFirst { it.id == s.id }.takeIf { it >= 0 } ?: 0 } ?: 0
        mainHandler.post { onShuffleChangeListener?.invoke(isShuffle) }
    }

    fun cycleRepeat() {
        repeatMode = repeatMode.next()
        mainHandler.post { onRepeatChangeListener?.invoke(repeatMode) }
    }

    fun stopMusic() {
        try {
            abandonAudioFocus()
            cancelNotificationThread()
            mediaSession?.isActive = false
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
            mediaPlayer = null
            releaseWakeLock()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        } catch (e: Exception) { Log.e(TAG, "stop: ${e.message}") }
    }

    fun getCurrentPosition(): Int = try { mediaPlayer?.currentPosition ?: 0 } catch (e: Exception) { 0 }
    fun getDuration(): Int = try { mediaPlayer?.duration ?: 0 } catch (e: Exception) { 0 }
    fun getCurrentSong(): Song? = playList.getOrNull(currentIndex)

    // ─── Core Playback ────────────────────────────────────────────────────────

    private fun playCurrent() {
        val song = playList.getOrNull(currentIndex) ?: return
        try {
            // Release old player cleanly
            mediaPlayer?.apply {
                try { if (isPlaying) stop() } catch (e: Exception) { }
                reset()
                release()
            }
            mediaPlayer = null

            if (!requestAudioFocus()) {
                Log.w(TAG, "No audio focus")
                return
            }
            hasAudioFocus = true

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(applicationContext, song.uri)
                prepare() // Synchronous — safe on non-UI thread
                start()

                setOnCompletionListener {
                    mainHandler.post {
                        when (repeatMode) {
                            RepeatMode.REPEAT_ONE -> playCurrent()
                            RepeatMode.REPEAT_ALL -> playNext()
                            RepeatMode.OFF -> {
                                if (currentIndex < playList.size - 1) playNext()
                                else {
                                    notifyPlayState(false)
                                    getCurrentSong()?.let { postNotification(it) }
                                }
                            }
                        }
                    }
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    mainHandler.post {
                        notifyPlayState(false)
                        getCurrentSong()?.let { postNotification(it) }
                    }
                    true
                }
            }

            // Notify listeners on main thread
            mainHandler.post {
                onSongChangeListener?.invoke(song)
                notifyPlayState(true)
            }
            updateSessionMetadata(song)
            updateSessionState()
            postNotification(song)

        } catch (e: Exception) {
            Log.e(TAG, "playCurrent error: ${e.message}")
            mainHandler.post { notifyPlayState(false) }
        }
    }

    private fun notifyPlayState(playing: Boolean) {
        // Always on main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onPlayStateChangeListener?.invoke(playing)
        } else {
            mainHandler.post { onPlayStateChangeListener?.invoke(playing) }
        }
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun cancelNotificationThread() {
        notificationThread?.interrupt()
        notificationThread = null
    }

    private fun postNotification(song: Song) {
        cancelNotificationThread()
        // Show immediately with no art
        try {
            startForeground(NOTIFICATION_ID, buildNotification(song, null))
        } catch (e: Exception) { }

        // Then load art in background
        notificationThread = Thread {
            var bitmap: Bitmap? = null
            try {
                song.albumArtUri?.let { uri ->
                    contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                        bitmap = BitmapFactory.decodeFileDescriptor(
                            fd.fileDescriptor, null,
                            BitmapFactory.Options().apply { inSampleSize = 2 }
                        )
                    }
                }
            } catch (e: Exception) { }

            if (!Thread.interrupted()) {
                val bmp = bitmap
                mainHandler.post {
                    try {
                        // Update media session with art
                        bmp?.let { b ->
                            mediaSession?.setMetadata(
                                MediaMetadataCompat.Builder()
                                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
                                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, b)
                                    .build()
                            )
                        }
                        val n = buildNotification(song, bmp)
                        startForeground(NOTIFICATION_ID, n)
                        try {
                            NotificationManagerCompat.from(this@MusicService).notify(NOTIFICATION_ID, n)
                        } catch (e: SecurityException) { }
                    } catch (e: Exception) { }
                }
            }
        }.also { it.start() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Music Player", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Music playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(song: Song, albumArt: Bitmap?): Notification {
        val f = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        fun svcIntent(action: String, reqCode: Int) = PendingIntent.getService(
            this, reqCode, Intent(this, MusicService::class.java).apply { this.action = action }, f)

        val playing = isPlaying
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText("${song.artist} • ${song.album}")
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(albumArt)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, NowPlayingActivity::class.java), f))
            .addAction(R.drawable.ic_skip_previous, "Prev",   svcIntent(ACTION_PREV, 1))
            .addAction(R.drawable.ic_rewind,        "-10s",   svcIntent(ACTION_REWIND, 2))
            .addAction(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                if (playing) "Pause" else "Play",
                svcIntent(ACTION_PLAY_PAUSE, 3)
            )
            .addAction(R.drawable.ic_forward,       "+10s",   svcIntent(ACTION_FORWARD, 4))
            .addAction(R.drawable.ic_skip_next,     "Next",   svcIntent(ACTION_NEXT, 5))
            .addAction(R.drawable.ic_close,         "Stop",   svcIntent(ACTION_STOP, 6))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 2, 4))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(playing)
            .setAutoCancel(false)
            .apply {
                albumArt?.let { bmp ->
                    try {
                        Palette.from(bmp).generate { p ->
                            p?.dominantSwatch?.rgb?.let { setColor(it) }
                        }
                    } catch (e: Exception) { }
                }
            }
            .build()
    }

    // ─── Media Session ────────────────────────────────────────────────────────

    private fun updateSessionMetadata(song: Song) {
        try {
            mediaSession?.setMetadata(MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
                .build())
        } catch (e: Exception) { }
    }

    private fun updateSessionState() {
        try {
            val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            mediaSession?.setPlaybackState(PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_FAST_FORWARD or PlaybackStateCompat.ACTION_REWIND or
                    PlaybackStateCompat.ACTION_STOP
                )
                .setState(state, getCurrentPosition().toLong(), 1f)
                .build())
        } catch (e: Exception) { }
    }

    // ─── Wake Lock ────────────────────────────────────────────────────────────

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) { }
        wakeLock = null
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onDestroy() {
        try {
            abandonAudioFocus()
            cancelNotificationThread()
            mediaSession?.apply { isActive = false; release() }
            mediaPlayer?.apply {
                try { if (isPlaying) stop() } catch (e: Exception) { }
                reset(); release()
            }
            mediaPlayer = null
            releaseWakeLock()
        } catch (e: Exception) { }
        super.onDestroy()
    }
}
