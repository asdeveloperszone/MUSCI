package com.asdeveloperszone.musicplayer
import android.os.CountDownTimer
object SleepTimerManager {
    private var timer: CountDownTimer? = null
    var remainingMs: Long = 0L
    var isRunning = false
    var onFinish: (() -> Unit)? = null
    var onTick: ((Long) -> Unit)? = null
    fun start(minutes: Int) {
        cancel()
        val ms = minutes * 60 * 1000L
        remainingMs = ms; isRunning = true
        timer = object : CountDownTimer(ms, 1000L) {
            override fun onTick(left: Long) { remainingMs = left; onTick?.invoke(left) }
            override fun onFinish() { isRunning = false; remainingMs = 0L; onFinish?.invoke() }
        }.start()
    }
    fun cancel() { timer?.cancel(); timer = null; isRunning = false; remainingMs = 0L }
    fun getFormattedTime(): String {
        val s = (remainingMs/1000).toInt()
        return String.format("%02d:%02d", s/60, s%60)
    }
}
