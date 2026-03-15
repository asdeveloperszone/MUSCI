package com.asdeveloperszone.musicplayer

import android.content.Context
import android.graphics.*
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

class VisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Style { BARS, WAVE }

    var style: Style = Style.BARS
    private var bytes: ByteArray = ByteArray(0)
    private var visualizer: Visualizer? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCC0000.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val gradientColors = intArrayOf(0xFFCC0000.toInt(), 0xFFFF6666.toInt())

    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId == 0) return
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer, data: ByteArray, rate: Int) {
                        bytes = data.clone(); postInvalidate()
                    }
                    override fun onFftDataCapture(v: Visualizer, data: ByteArray, rate: Int) {}
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        } catch (e: Exception) { }
    }

    fun release() {
        try { visualizer?.enabled = false; visualizer?.release() } catch (e: Exception) { }
        visualizer = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bytes.isEmpty()) { drawIdle(canvas); return }
        when (style) {
            Style.BARS -> drawBars(canvas)
            Style.WAVE -> drawWave(canvas)
        }
    }

    private fun drawBars(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val barCount = 64
        val barWidth = w / (barCount * 2f)
        val step = bytes.size / barCount
        paint.shader = LinearGradient(0f, h, 0f, 0f,
            gradientColors, null, Shader.TileMode.CLAMP)
        paint.strokeWidth = barWidth

        for (i in 0 until barCount) {
            val raw = bytes[i * step].toInt() and 0xFF
            val barH = (raw / 255f) * h * 0.9f + h * 0.05f
            val x = i * barWidth * 2 + barWidth
            paint.alpha = 180 + (raw / 255f * 75).toInt()
            canvas.drawLine(x, h, x, h - barH, paint)
        }
    }

    private fun drawWave(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val path = Path()
        val step = bytes.size / width.coerceAtLeast(1)
        paint.shader = null; paint.color = 0xFFCC0000.toInt()
        paint.strokeWidth = 3f; paint.style = Paint.Style.STROKE

        val pts = bytes.filterIndexed { i, _ -> i % step.coerceAtLeast(1) == 0 }
        if (pts.isEmpty()) { drawIdle(canvas); return }

        pts.forEachIndexed { i, b ->
            val x = i * w / pts.size
            val y = h / 2 + (b.toInt() / 128f) * h * 0.45f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawIdle(canvas: Canvas) {
        // Animated idle wave using sin
        val w = width.toFloat(); val h = height.toFloat()
        val path = Path()
        paint.shader = null; paint.color = 0x44CC0000
        paint.strokeWidth = 2f; paint.style = Paint.Style.STROKE
        val t = System.currentTimeMillis() / 500.0
        for (x in 0..width) {
            val y = h / 2 + sin(x * 0.05 + t).toFloat() * h * 0.1f
            if (x == 0) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y)
        }
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        postInvalidateDelayed(50)
    }
}
