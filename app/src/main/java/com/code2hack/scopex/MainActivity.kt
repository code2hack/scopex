package com.code2hack.scopex

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        content.addView(text("scopex debug home", 24f, Typeface.BOLD))
        content.addView(text("ScopeX simulator placeholder"))
        content.addView(text("MediaProjection capture placeholder"))
        content.addView(text("Diagnostics placeholder"))
        content.addView(text("Build ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))

        setContentView(ScrollView(this).apply {
            addView(content)
        })
    }

    private fun text(value: String, sizeSp: Float = 18f, style: Int = Typeface.NORMAL) =
        TextView(this).apply {
            text = value
            textSize = sizeSp
            typeface = Typeface.DEFAULT_BOLD.takeIf { style == Typeface.BOLD } ?: Typeface.DEFAULT
            gravity = Gravity.START
            setPadding(0, 0, 0, 18)
        }
}
