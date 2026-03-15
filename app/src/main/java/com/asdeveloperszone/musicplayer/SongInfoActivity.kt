package com.asdeveloperszone.musicplayer

import android.app.Activity
import android.content.*
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SongInfoActivity : AppCompatActivity(), ServiceConnection {

    private var svc: MusicService? = null
    private var bound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_info)
        findViewById<ImageButton>(R.id.btnSongInfoBack).setOnClickListener { finish() }
        bindService(Intent(this, MusicService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        svc = (service as MusicService.MusicBinder).getService()
        bound = true
        svc?.currentSong()?.let { loadSongInfo(it) }
    }

    private fun loadSongInfo(song: Song) {
        Thread {
            val retriever = MediaMetadataRetriever()
            var bitrate = "Unknown"
            var format  = "Unknown"
            var fileSize = "Unknown"
            var filePath = ""

            try {
                retriever.setDataSource(this, song.uri)
                val br = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toLongOrNull()?.div(1000)
                bitrate = if (br != null) "$br kbps" else "Unknown"
                retriever.release()
            } catch (e: Exception) {
                try { retriever.release() } catch (e2: Exception) { }
            }

            try {
                val proj = arrayOf(
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.MIME_TYPE
                )
                contentResolver.query(song.uri, proj, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        filePath = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)) ?: ""
                        val bytes = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE))
                        val mime  = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)) ?: ""
                        format   = mime.substringAfterLast("/").uppercase()
                        fileSize = formatSize(bytes)
                    }
                }
            } catch (e: Exception) { }

            if (format == "Unknown" || format.isEmpty()) {
                val ext = filePath.substringAfterLast(".", "").uppercase()
                if (ext.isNotEmpty()) format = ext
            }

            val fBitrate  = bitrate
            val fFormat   = format
            val fFileSize = fileSize
            val fPath     = filePath

            runOnUiThread { updateUI(song, fBitrate, fFormat, fFileSize, fPath) }
        }.start()
    }

    private fun updateUI(song: Song, bitrate: String, format: String, fileSize: String, path: String) {
        fun set(id: Int, value: String) = try { findViewById<TextView>(id).text = value } catch (e: Exception) { }
        set(R.id.tvInfoTitle,    song.title)
        set(R.id.tvInfoArtist,   song.artist)
        set(R.id.tvInfoAlbum,    song.album)
        set(R.id.tvInfoDuration, song.getDurationFormatted())
        set(R.id.tvInfoFormat,   format)
        set(R.id.tvInfoBitrate,  bitrate)
        set(R.id.tvInfoSize,     fileSize)
        set(R.id.tvInfoPath,     path)

        findViewById<Button>(R.id.btnShareSong).setOnClickListener    { shareSong(song) }
        findViewById<Button>(R.id.btnSetRingtone).setOnClickListener  { setAsRingtone(song) }
        findViewById<Button>(R.id.btnDeleteSong).setOnClickListener   { confirmDelete(song) }
    }

    private fun shareSong(song: Song) {
        try {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, song.uri)
                    putExtra(Intent.EXTRA_TEXT, "${song.title} - ${song.artist}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share Song"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot share this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setAsRingtone(song: Song) {
        // Check WRITE_SETTINGS permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
            AlertDialog.Builder(this, R.style.SortDialogTheme)
                .setTitle("Permission Required")
                .setMessage("Music Player needs permission to modify system settings.\n\nTap Open Settings, find Music Player, and enable 'Modify system settings'.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        AlertDialog.Builder(this, R.style.SortDialogTheme)
            .setTitle("Set \"${song.title}\" as")
            .setItems(arrayOf("Phone Ringtone", "Notification Sound", "Alarm Sound")) { _, which ->
                val type = when (which) {
                    0 -> RingtoneManager.TYPE_RINGTONE
                    1 -> RingtoneManager.TYPE_NOTIFICATION
                    else -> RingtoneManager.TYPE_ALARM
                }
                applyRingtone(song, type, when(which) {
                    0 -> "Phone Ringtone"; 1 -> "Notification"; else -> "Alarm"
                })
            }.show()
    }

    private fun applyRingtone(song: Song, type: Int, label: String) {
        try {
            val cv = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_RINGTONE,     type == RingtoneManager.TYPE_RINGTONE)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, type == RingtoneManager.TYPE_NOTIFICATION)
                put(MediaStore.Audio.Media.IS_ALARM,        type == RingtoneManager.TYPE_ALARM)
            }
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
            try { contentResolver.update(uri, cv, null, null) } catch (e: Exception) { }
            Settings.System.putString(contentResolver,
                when (type) {
                    RingtoneManager.TYPE_NOTIFICATION -> Settings.System.NOTIFICATION_SOUND
                    RingtoneManager.TYPE_ALARM        -> Settings.System.ALARM_ALERT
                    else                              -> Settings.System.RINGTONE
                }, uri.toString())
            Toast.makeText(this, "\"${song.title}\" set as $label ✓", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Fallback to RingtoneManager
            try {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                RingtoneManager.setActualDefaultRingtoneUri(this, type, uri)
                Toast.makeText(this, "\"${song.title}\" set as $label ✓", Toast.LENGTH_SHORT).show()
            } catch (e2: Exception) {
                Toast.makeText(this, "Error: ${e2.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDelete(song: Song) {
        AlertDialog.Builder(this, R.style.SortDialogTheme)
            .setTitle("Delete Song")
            .setMessage("Permanently delete \"${song.title}\"?")
            .setPositiveButton("Delete") { _, _ -> deleteSong(song) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSong(song: Song) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val req = MediaStore.createDeleteRequest(contentResolver,
                    listOf(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)))
                startIntentSenderForResult(req.intentSender, 1001, null, 0, 0, 0)
            } else {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                val rows = contentResolver.delete(uri, null, null)
                if (rows > 0) {
                    svc?.removeFromQueue(song.id)
                    Toast.makeText(this, "Song deleted", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK); finish()
                } else {
                    Toast.makeText(this, "Could not delete — check permissions", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(rc: Int, result: Int, data: Intent?) {
        super.onActivityResult(rc, result, data)
        if (rc == 1001 && result == Activity.RESULT_OK) {
            svc?.currentSong()?.let { svc?.removeFromQueue(it.id) }
            Toast.makeText(this, "Song deleted", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK); finish()
        }
    }

    private fun formatSize(bytes: Long) = when {
        bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
        bytes >= 1024      -> "%.1f KB".format(bytes / 1024.0)
        else               -> "$bytes B"
    }

    override fun onServiceDisconnected(n: ComponentName?) { bound = false; svc = null }
    override fun onDestroy() {
        if (bound) { try { unbindService(this) } catch (e: Exception) { }; bound = false }
        super.onDestroy()
    }
}
