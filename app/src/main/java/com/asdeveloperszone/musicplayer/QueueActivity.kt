package com.asdeveloperszone.musicplayer

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class QueueActivity : AppCompatActivity(), ServiceConnection {

    private var svc: MusicService? = null
    private var bound = false
    private lateinit var adapter: QueueAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_queue)
        findViewById<ImageButton>(R.id.btnQueueBack).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvQueue)
        rv.layoutManager = LinearLayoutManager(this)

        adapter = QueueAdapter { fromPos, toPos ->
            svc?.moveQueueItem(fromPos, toPos)
        }
        rv.adapter = adapter

        // Drag to reorder
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder): Boolean {
                val from = vh.adapterPosition
                val to   = target.adapterPosition
                adapter.moveItem(from, to)
                svc?.moveQueueItem(from, to)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
        })
        touchHelper.attachToRecyclerView(rv)

        bindService(Intent(this, MusicService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    inner class QueueAdapter(
        private val onMove: (Int, Int) -> Unit
    ) : RecyclerView.Adapter<QueueAdapter.VH>() {
        private var queue: MutableList<Song> = mutableListOf()
        private var currentIndex: Int = 0

        fun setQueue(songs: List<Song>, index: Int) {
            queue = songs.toMutableList(); currentIndex = index; notifyDataSetChanged()
        }

        fun moveItem(from: Int, to: Int) {
            val item = queue.removeAt(from)
            queue.add(to, item)
            notifyItemMoved(from, to)
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val art: ImageView     = v.findViewById(R.id.ivQueueArt)
            val title: TextView    = v.findViewById(R.id.tvQueueTitle)
            val artist: TextView   = v.findViewById(R.id.tvQueueArtist)
            val drag: ImageView    = v.findViewById(R.id.ivDragHandle)
            val playing: View      = v.findViewById(R.id.viewQueuePlaying)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_queue, p, false))

        override fun getItemCount() = queue.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val song   = queue[pos]
            val active = pos == currentIndex
            h.title.text  = song.title
            h.artist.text = song.artist
            h.playing.visibility = if (active) View.VISIBLE else View.INVISIBLE
            h.title.setTextColor(if (active) 0xFFCC0000.toInt() else 0xFFFFFFFF.toInt())
            Glide.with(h.itemView.context).load(song.albumArtUri)
                .placeholder(R.drawable.ic_music_note).centerCrop().into(h.art)
            h.itemView.setOnClickListener {
                svc?.jumpToQueueIndex(pos)
                finish()
            }
        }
    }

    override fun onServiceConnected(n: ComponentName?, s: IBinder?) {
        try {
            svc = (s as MusicService.MusicBinder).getService()
            bound = true
            val queue = svc?.getQueue() ?: emptyList()
            val idx   = svc?.getCurrentIndex() ?: 0
            adapter.setQueue(queue, idx)
            findViewById<TextView>(R.id.tvQueueCount).text = "${queue.size} songs in queue"
        } catch (e: Exception) { }
    }

    override fun onServiceDisconnected(n: ComponentName?) { bound = false; svc = null }
    override fun onDestroy() {
        if (bound) { try { unbindService(this) } catch (e: Exception) { }; bound = false }
        super.onDestroy()
    }
}
