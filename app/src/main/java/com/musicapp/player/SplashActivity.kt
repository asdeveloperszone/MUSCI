package com.musicapp.player

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

        val ivLogo = findViewById<ImageView>(R.id.ivSplashLogo)
        val tvAppName = findViewById<TextView>(R.id.tvSplashName)
        val tvTagline = findViewById<TextView>(R.id.tvSplashTagline)

        // Logo: scale + fade in
        val logoAnim = AnimationSet(true).apply {
            addAnimation(ScaleAnimation(0f, 1f, 0f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f).apply { duration = 700 })
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 700 })
            fillAfter = true
        }

        // App name: slide up + fade
        val nameAnim = AnimationSet(true).apply {
            addAnimation(TranslateAnimation(0f, 0f, 60f, 0f).apply { duration = 600 })
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 600 })
            startOffset = 500
            fillAfter = true
        }

        // Tagline: fade in with delay
        val taglineAnim = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 800 })
            addAnimation(TranslateAnimation(0f, 0f, 30f, 0f).apply { duration = 800 })
            startOffset = 900
            fillAfter = true
        }

        ivLogo.startAnimation(logoAnim)
        tvAppName.startAnimation(nameAnim)
        tvTagline.startAnimation(taglineAnim)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 2800)
    }
}
