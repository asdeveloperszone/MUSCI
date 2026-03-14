package com.asdeveloperszone.musicplayer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.*
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val ivLogo    = findViewById<ImageView>(R.id.ivSplashLogo)
        val tvName    = findViewById<TextView>(R.id.tvSplashName)
        val tvTagline = findViewById<TextView>(R.id.tvSplashTagline)

        // Logo bounce in
        AnimationSet(true).apply {
            addAnimation(ScaleAnimation(0f, 1f, 0f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f).apply { duration = 600 })
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 600 })
            fillAfter = true
            ivLogo.startAnimation(this)
        }

        // Name slide up
        AnimationSet(true).apply {
            addAnimation(TranslateAnimation(0f, 0f, 50f, 0f).apply { duration = 500 })
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 500 })
            startOffset = 400
            fillAfter = true
            tvName.startAnimation(this)
        }

        // Tagline fade in
        AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 600 })
            addAnimation(TranslateAnimation(0f, 0f, 20f, 0f).apply { duration = 600 })
            startOffset = 700
            fillAfter = true
            tvTagline.startAnimation(this)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 2500)
    }
}
