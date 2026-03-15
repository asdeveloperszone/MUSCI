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
import android.os.*
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.palette.graphics.Palette
import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
class MusicService : Service() {
    companion object {
        const val CHANNEL_ID    = "MusicPlayerChannel"
        const val NOTIF_ID      = 1
        const val ACTION_PLAY_PAUSE = "com.asdeveloperszone.musicplayer.PLAY_PAUSE"
        const val ACTION_NEXT       = "com.asdeveloperszone.musicplayer.NEXT"
        const val ACTION_PREV       = "com.asdeveloperszone.musicplayer.PREV"
        const val ACTION_STOP       = "com.asdeveloperszone.musicplayer.STOP"
        const val ACTION_REWIND     = "com.asdeveloperszone.musicplayer.REWIND"
        const val ACTION_FORWARD    = "com.asdeveloperszone.musicplayer.FORWARD"
        const val SEEK_MS = 10_000
        private const val TAG = "MusicService"
    }
    private val binder = MusicBinder()
    inner class MusicBinder : Binder() { fun getService() = this@MusicService }
    private var player: MediaPlayer? = null
    private var originalList: List<Song> = emptyList()
    private var queue: MutableList<Song> = mutableListOf()
    private var index = 0
    val isPlaying: Boolean get() = try { player?.isPlaying == true } catch (e: Exception) { false }
    var isShuffle = false
    var repeatMode = RepeatMode.OFF
    var onSongChange:    ((Song)       -> Unit)? = null
    var onPlayState:     ((Boolean)    -> Unit)? = null
    var onShuffleChange: ((Boolean)    -> Unit)? = null
    var onRepeatChange:  ((RepeatMode) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())
    private var notifThread: Thread? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSessionCompat? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        main.post {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS              -> pause()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT    -> pause()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(0.2f, 0.2f)
                AudioManager.AUDIOFOCUS_GAIN              -> player?.setVolume(1f, 1f)
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createChannel(); initSession(); acquireWake()
    }
    override fun onBind(intent: Intent): IBinder = binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT       -> next()
            ACTION_PREV       -> previous()
            ACTION_STOP       -> stop()
            ACTION_REWIND     -> rewind()
            ACTION_FORWARD    -> forward()
        }
        return START_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        currentSong()?.let { startForeground(NOTIF_ID, buildNotif(it, null)) }
    }
    fun load(songs: List<Song>, startAt: Int = 0) {
        if (songs.isEmpty()) return
        originalList = songs.toList()
        queue = (if (isShuffle) songs.shuffled() else songs).toMutableList()
        index = startAt.coerceIn(0, queue.size - 1)
        play()
    }
    fun togglePlayPause() { if (isPlaying) pause() else resume() }
    fun next() { if (queue.isEmpty()) return; index = (index+1) % queue.size; play() }
    fun previous() {
        if (queue.isEmpty()) return
        if ((player?.currentPosition ?: 0) > 3000) { seekTo(0); return }
        index = if (index <= 0) queue.size-1 else index-1; play()
    }
    fun rewind()  { seekTo(((player?.currentPosition ?: 0) - SEEK_MS).coerceAtLeast(0)) }
    fun forward() { seekTo(((player?.currentPosition ?: 0) + SEEK_MS).coerceAtMost(player?.duration ?: 0)) }
    fun seekTo(pos: Int) { try { player?.seekTo(pos); updateSession() } catch (e: Exception) { } }
    fun shuffle() {
        isShuffle = !isShuffle
        val cur = currentSong()
        queue = (if (isShuffle) originalList.shuffled() else originalList).toMutableList()
        index = cur?.let { s -> queue.indexOfFirst { it.id == s.id }.coerceAtLeast(0) } ?: 0
        fire { onShuffleChange?.invoke(isShuffle) }
    }
    fun cycleRepeat() { repeatMode = repeatMode.next(); fire { onRepeatChange?.invoke(repeatMode) } }
    fun stop() {
        try {
            notifThread?.interrupt()
            dropFocus()
            mediaSession?.isActive = false
            player?.apply { try { if (isPlaying) stop() } catch (e: Exception) { }; reset(); release() }
            player = null; releaseWake()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
            else { @Suppress("DEPRECATION") stopForeground(true) }
            stopSelf()
        } catch (e: Exception) { Log.e(TAG, "stop: ${e.message}") }
    }
    fun position(): Int = try { player?.currentPosition ?: 0 } catch (e: Exception) { 0 }
    fun duration(): Int = try { player?.duration ?: 0 } catch (e: Exception) { 0 }
    fun currentSong(): Song? = queue.getOrNull(index)
    private fun play() {
        val song = queue.getOrNull(index) ?: return
        try {
            player?.apply { try { if (isPlaying) stop() } catch (e: Exception) { }; reset(); release() }
            player = null
            if (!focus()) return
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build())
                setDataSource(applicationContext, song.uri)
                prepare(); start()
                setOnCompletionListener {
                    main.post {
                        when (repeatMode) {
                            RepeatMode.REPEAT_ONE -> play()
                            RepeatMode.REPEAT_ALL -> next()
                            RepeatMode.OFF -> { if (index < queue.size-1) next() else fire { onPlayState?.invoke(false) } }
                        }
                    }
                }
                setOnErrorListener { _, w, e -> Log.e(TAG, "err w=$w e=$e"); main.post { fire { onPlayState?.invoke(false) } }; true }
            }
            fire { onSongChange?.invoke(song); onPlayState?.invoke(true) }
            initEqualizer()
            updateMeta(song); updateSession(); postNotif(song)
        } catch (e: Exception) { Log.e(TAG, "play: ${e.message}"); fire { onPlayState?.invoke(false) } }
    }
    private fun pause() {
        try { if (player?.isPlaying == true) { player?.pause(); fire { onPlayState?.invoke(false) }; currentSong()?.let { postNotif(it) }; updateSession() } }
        catch (e: Exception) { Log.e(TAG, "pause: ${e.message}") }
    }
    private fun resume() {
        try { if (player != null && player?.isPlaying == false) { if (!focus()) return; player?.start(); fire { onPlayState?.invoke(true) }; currentSong()?.let { postNotif(it) }; updateSession() } }
        catch (e: Exception) { Log.e(TAG, "resume: ${e.message}") }
    }
    private fun focus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusListener, main).build()
            focusRequest = req
            audioManager?.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
    private fun dropFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            else { @Suppress("DEPRECATION") audioManager?.abandonAudioFocus(focusListener) }
        } catch (e: Exception) { }
    }
    private fun postNotif(song: Song) {
        notifThread?.interrupt()
        try { startForeground(NOTIF_ID, buildNotif(song, null)) } catch (e: Exception) { }
        notifThread = Thread {
            var bmp: Bitmap? = null
            try { song.albumArtUri?.let { uri -> contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                bmp = BitmapFactory.decodeFileDescriptor(fd.fileDescriptor, null, BitmapFactory.Options().apply { inSampleSize = 2 })
            } } } catch (e: Exception) { }
            if (Thread.interrupted()) return@Thread
            val art = bmp
            main.post {
                try {
                    art?.let { b -> mediaSession?.setMetadata(MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, b).build()) }
                    val n = buildNotif(song, art)
                    startForeground(NOTIF_ID, n)
                    try { NotificationManagerCompat.from(this@MusicService).notify(NOTIF_ID, n) } catch (e: SecurityException) { }
                } catch (e: Exception) { }
            }
        }.also { it.start() }
    }
    private fun buildNotif(song: Song, art: Bitmap?): Notification {
        val f = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        fun svc(action: String, req: Int) = PendingIntent.getService(this, req, Intent(this, MusicService::class.java).apply { this.action = action }, f)
        val playing = isPlaying
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title).setContentText("${song.artist} • ${song.album}")
            .setSmallIcon(R.drawable.ic_music_note).setLargeIcon(art)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, NowPlayingActivity::class.java), f))
            .addAction(R.drawable.ic_skip_previous, "Prev",  svc(ACTION_PREV, 1))
            .addAction(R.drawable.ic_rewind,        "-10s",  svc(ACTION_REWIND, 2))
            .addAction(if (playing) R.drawable.ic_pause else R.drawable.ic_play, if (playing) "Pause" else "Play", svc(ACTION_PLAY_PAUSE, 3))
            .addAction(R.drawable.ic_forward,       "+10s",  svc(ACTION_FORWARD, 4))
            .addAction(R.drawable.ic_skip_next,     "Next",  svc(ACTION_NEXT, 5))
            .addAction(R.drawable.ic_close,         "Stop",  svc(ACTION_STOP, 6))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession?.sessionToken).setShowActionsInCompactView(0, 2, 4))
            .setPriority(NotificationCompat.PRIORITY_LOW).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(playing).setAutoCancel(false)
            .apply { art?.let { b -> try { Palette.from(b).generate { p -> p?.dominantSwatch?.rgb?.let { setColor(it) } } } catch (e: Exception) { } } }
            .build()
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Music Player", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false); lockscreenVisibility = Notification.VISIBILITY_PUBLIC })
        }
    }
    private fun initSession() {
        mediaSession = MediaSessionCompat(this, "MusicPlayer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onSkipToNext() = next()
                override fun onSkipToPrevious() = previous()
                override fun onStop() = stop()
                override fun onSeekTo(pos: Long) = seekTo(pos.toInt())
                override fun onFastForward() = forward()
                override fun onRewind() = rewind()
            }); isActive = true
        }
    }
    private fun updateMeta(song: Song) {
        try { mediaSession?.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration).build()) } catch (e: Exception) { }
    }
    private fun updateSession() {
        try {
            val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            mediaSession?.setPlaybackState(PlaybackStateCompat.Builder()
                .setState(state, position().toLong(), 1f)
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_FAST_FORWARD or PlaybackStateCompat.ACTION_REWIND or PlaybackStateCompat.ACTION_STOP)
                .build())
        } catch (e: Exception) { }
    }
    private fun acquireWake() {
        try { val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusicPlayer::Wake").apply { acquire(12*60*60*1000L) }
        } catch (e: Exception) { }
    }
    private fun releaseWake() { try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (e: Exception) { }; wakeLock = null }
    private fun fire(block: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block) }
    override fun onDestroy() {
        try { notifThread?.interrupt(); dropFocus(); mediaSession?.apply { isActive = false; release() }
            player?.apply { try { if (isPlaying) stop() } catch (e: Exception) { }; reset(); release() }
            player = null; releaseWake()
        } catch (e: Exception) { }
        super.onDestroy()
    }

    // ── Audio Effects ─────────────────────────────────────────────────────────
    fun getAudioSessionId(): Int = try { player?.audioSessionId ?: 0 } catch (e: Exception) { 0 }

    private var crossfadeDuration = 0

    fun setCrossfade(seconds: Int) { crossfadeDuration = seconds }

    fun setPlaybackSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = player?.playbackParams?.apply { this.speed = speed }
                if (params != null) player?.playbackParams = params
            } catch (e: Exception) { }
        }
    }

    fun setPlaybackPitch(pitch: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = player?.playbackParams?.apply { this.pitch = pitch }
                if (params != null) player?.playbackParams = params
            } catch (e: Exception) { }
        }
    }

    fun applyPlaybackSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val sp = getSharedPreferences("playback_settings", Context.MODE_PRIVATE)
                val speed = sp.getFloat("playback_speed", 1.0f)
                val pitch = sp.getFloat("playback_pitch", 1.0f)
                if (speed != 1.0f || pitch != 1.0f) {
                    val params = android.media.PlaybackParams().apply {
                        this.speed = speed; this.pitch = pitch
                    }
                    player?.playbackParams = params
                }
            } catch (e: Exception) { }
        }
    }

    // ── Equalizer — lives in service so it persists ───────────────────────────
    fun initEqualizer() {
        val sessionId = try { player?.audioSessionId ?: 0 } catch (e: Exception) { 0 }
        if (sessionId == 0) return
        try {
            if (equalizer == null) {
                equalizer = Equalizer(0, sessionId).apply { enabled = true }
            }
            if (bassBoost == null) {
                bassBoost = BassBoost(0, sessionId).apply { enabled = true }
            }
            // Restore saved EQ settings
            val prefs = getSharedPreferences("eq_settings", Context.MODE_PRIVATE)
            val numBands = equalizer!!.numberOfBands.toInt()
            for (i in 0 until numBands) {
                val saved = prefs.getInt("band_$i", 0)
                if (saved != 0) equalizer!!.setBandLevel(i.toShort(), saved.toShort())
            }
            val savedBass = prefs.getInt("bass_boost", 0)
            if (savedBass > 0) bassBoost!!.setStrength(savedBass.toShort())
        } catch (e: Exception) { }
    }

    fun getEqualizer(): Equalizer? = equalizer

    fun setEqBand(band: Int, level: Short) {
        try {
            equalizer?.setBandLevel(band.toShort(), level)
            getSharedPreferences("eq_settings", Context.MODE_PRIVATE)
                .edit().putInt("band_$band", level.toInt()).apply()
        } catch (e: Exception) { }
    }

    fun setBassBoost(strength: Int) {
        try {
            bassBoost?.setStrength(strength.toShort())
            getSharedPreferences("eq_settings", Context.MODE_PRIVATE)
                .edit().putInt("bass_boost", strength).apply()
        } catch (e: Exception) { }
    }

    private fun releaseEqualizer() {
        try { equalizer?.release() } catch (e: Exception) { }
        try { bassBoost?.release() } catch (e: Exception) { }
        equalizer = null; bassBoost = null
    }

    // ── Queue Management ──────────────────────────────────────────────────────
    fun getQueue(): List<Song> = queue.toList()
    fun getCurrentIndex(): Int = index

    fun moveQueueItem(from: Int, to: Int) {
        if (from < 0 || to < 0 || from >= queue.size || to >= queue.size) return
        val item = queue.removeAt(from)
        queue.add(to, item)
        // Update current index if needed
        index = when {
            from == index -> to
            from < index && to >= index -> index - 1
            from > index && to <= index -> index + 1
            else -> index
        }
    }

    fun jumpToQueueIndex(pos: Int) {
        if (pos in queue.indices) { index = pos; play() }
    }
}
