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
import java.io.File

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
        val song = svc?.currentSong() ?: return
        loadSongInfo(song)
    }

    private fun loadSongInfo(song: Song) {
        Thread {
            val retriever = MediaMetadataRetriever()
            var bitrate = "Unknown"
            var format  = "Unknown"
            var fileSize = "Unknown"
            var sampleRate = "Unknown"
            var channels = "Unknown"
            var filePath = "Unknown"

            try {
                retriever.setDataSource(this, song.uri)
                bitrate    = "${retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.div(1000) ?: "?"} kbps"
                sampleRate = "${retriever.extractMetadata(23) ?: "?"} Hz"  // METADATA_KEY_SAMPLERATE = 23
                channels   = retriever.extractMetadata(24) ?: "?"          // METADATA_KEY_NUM_TRACKS = 24
                retriever.release()

                // Get file path and size
                val projection = arrayOf(MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE)
                contentResolver.query(song.uri, projection, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        filePath = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)) ?: ""
                        val sizeBytes = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE))
                        val mime = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)) ?: ""
                        format   = mime.substringAfterLast("/").uppercase()
                        fileSize = formatSize(sizeBytes)
                    }
                }
                if (filePath.isEmpty()) filePath = song.uri.path ?: "Unknown"
            } catch (e: Exception) {
                try { retriever.release() } catch (e2: Exception) { }
            }

            val ext = filePath.substringAfterLast(".", "").uppercase()
            if (format == "Unknown" && ext.isNotEmpty()) format = ext

            val finalBitrate  = bitrate
            val finalFormat   = format
            val finalFileSize = fileSize
            val finalPath     = filePath

            runOnUiThread {
                updateUI(song, finalBitrate, finalFormat, finalFileSize, finalPath)
            }
        }.start()
    }

    private fun updateUI(song: Song, bitrate: String, format: String, fileSize: String, path: String) {
        fun row(id: Int, value: String) {
            try { findViewById<TextView>(id).text = value } catch (e: Exception) { }
        }

        row(R.id.tvInfoTitle,   song.title)
        row(R.id.tvInfoArtist,  song.artist)
        row(R.id.tvInfoAlbum,   song.album)
        row(R.id.tvInfoDuration,song.getDurationFormatted())
        row(R.id.tvInfoFormat,  format)
        row(R.id.tvInfoBitrate, bitrate)
        row(R.id.tvInfoSize,    fileSize)
        row(R.id.tvInfoPath,    path)

        // Share button
        findViewById<Button>(R.id.btnShareSong).setOnClickListener {
            shareSong(song)
        }

        // Set as ringtone
        findViewById<Button>(R.id.btnSetRingtone).setOnClickListener {
            setAsRingtone(song)
        }

        // Delete song
        findViewById<Button>(R.id.btnDeleteSong).setOnClickListener {
            confirmDelete(song)
        }
    }

    private fun shareSong(song: Song) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, song.uri)
                putExtra(Intent.EXTRA_SUBJECT, song.title)
                putExtra(Intent.EXTRA_TEXT, "${song.title} - ${song.artist}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Song"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot share this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setAsRingtone(song: Song) {
        // Android 6+ requires WRITE_SETTINGS permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
            AlertDialog.Builder(this, R.style.SortDialogTheme)
                .setTitle("Permission Required")
                .setMessage("Allow Music Player to modify system settings to set ringtone.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val options = arrayOf("Phone Ringtone", "Notification Sound", "Alarm Sound")
        AlertDialog.Builder(this, R.style.SortDialogTheme)
            .setTitle("Set as")
            .setItems(options) { _, which ->
                val type = when (which) {
                    0 -> RingtoneManager.TYPE_RINGTONE
                    1 -> RingtoneManager.TYPE_NOTIFICATION
                    else -> RingtoneManager.TYPE_ALARM
                }
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.IS_RINGTONE,     type == RingtoneManager.TYPE_RINGTONE)
                        put(MediaStore.Audio.Media.IS_NOTIFICATION, type == RingtoneManager.TYPE_NOTIFICATION)
                        put(MediaStore.Audio.Media.IS_ALARM,        type == RingtoneManager.TYPE_ALARM)
                    }
                    contentResolver.update(song.uri, values, null, null)
                    RingtoneManager.setActualDefaultRingtoneUri(this, type, song.uri)
                    Toast.makeText(this, "\"${song.title}\" set as ${options[which]}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun confirmDelete(song: Song) {
        AlertDialog.Builder(this, R.style.SortDialogTheme)
            .setTitle("Delete Song")
            .setMessage("Delete \"${song.title}\"?\n\nThis will permanently remove the file from your device.")
            .setPositiveButton("Delete") { _, _ -> deleteSong(song) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSong(song: Song) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ — use MediaStore delete request
                val deleteRequest = MediaStore.createDeleteRequest(contentResolver, listOf(song.uri))
                startIntentSenderForResult(deleteRequest.intentSender, 1001, null, 0, 0, 0)
            } else {
                // Android 9/10 — direct delete
                val deleted = contentResolver.delete(song.uri, null, null)
                if (deleted > 0) {
                    Toast.makeText(this, "Song deleted", Toast.LENGTH_SHORT).show()
                    svc?.removeFromQueue(song.id)
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this, "Could not delete song", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Song deleted", Toast.LENGTH_SHORT).show()
            svc?.currentSong()?.let { svc?.removeFromQueue(it.id) }
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_048_576 -> String.format("%.2f MB", bytes / 1_048_576.0)
            bytes >= 1024      -> String.format("%.1f KB", bytes / 1024.0)
            else               -> "$bytes B"
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) { bound = false; svc = null }
    override fun onDestroy() {
        if (bound) { try { unbindService(this) } catch (e: Exception) { }; bound = false }
        super.onDestroy()
    }
}
