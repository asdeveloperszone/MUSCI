package com.asdeveloperszone.musicplayer

import android.content.Context
import android.graphics.*
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class VisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bytes: ByteArray = ByteArray(0)
    private var visualizer: Visualizer? = null
    private var attached = false

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId == 0) return
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer, data: ByteArray, rate: Int) {
                        bytes = data.clone()
                        postInvalidate()
                    }
                    override fun onFftDataCapture(v: Visualizer, data: ByteArray, rate: Int) {}
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
            attached = true
        } catch (e: Exception) {
            attached = false
        }
    }

    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) { }
        visualizer = null
        attached = false
        bytes = ByteArray(0)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (bytes.isEmpty() || !attached) {
            drawIdle(canvas, w, h)
            return
        }
        drawBars(canvas, w, h)
    }

    private fun drawBars(canvas: Canvas, w: Float, h: Float) {
        val barCount  = 48
        val gap       = 3f
        val barWidth  = (w / barCount) - gap
        val step      = (bytes.size / barCount).coerceAtLeast(1)

        for (i in 0 until barCount) {
            val raw        = (bytes.getOrNull(i * step)?.toInt() ?: 128).and(0xFF)
            val normalized = raw / 255f
            val barH       = (normalized * h * 0.9f).coerceAtLeast(4f)
            val x          = i * (barWidth + gap)
            val top        = h - barH

            // Gradient: bottom = dark red, top = bright red/pink
            barPaint.shader = LinearGradient(
                x, h, x, top,
                intArrayOf(0xFFAA0000.toInt(), 0xFFFF4466.toInt()),
                null, Shader.TileMode.CLAMP)
            barPaint.alpha = (160 + normalized * 95).toInt().coerceIn(0, 255)

            rect.set(x, top, x + barWidth, h)
            canvas.drawRoundRect(rect, 3f, 3f, barPaint)
        }
        barPaint.shader = null
    }

    private fun drawIdle(canvas: Canvas, w: Float, h: Float) {
        val barCount = 48
        val gap      = 3f
        val barWidth = (w / barCount) - gap
        val t        = System.currentTimeMillis() / 700.0

        barPaint.shader = null
        barPaint.color  = 0x44CC0000

        for (i in 0 until barCount) {
            val wave = sin(i * 0.35 + t).toFloat()
            val barH = (h * 0.06f + wave * h * 0.05f).coerceAtLeast(2f)
            val x    = i * (barWidth + gap)
            rect.set(x, h - barH, x + barWidth, h)
            canvas.drawRoundRect(rect, 2f, 2f, barPaint)
        }
        postInvalidateDelayed(60)
    }
}
