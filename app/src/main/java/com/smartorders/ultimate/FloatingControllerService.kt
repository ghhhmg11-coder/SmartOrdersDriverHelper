package com.smartorders.ultimate

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView

class FloatingControllerService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var overlayView: View
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.floating_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 150
        }

        val toggleBtn = overlayView.findViewById<ImageButton>(R.id.btn_floating_toggle)
        val statusText = overlayView.findViewById<TextView>(R.id.tv_floating_status)

        updateToggleUI(toggleBtn, statusText)

        toggleBtn.setOnClickListener {
            JeenyUltimateService.isAutoAcceptEnabled = !JeenyUltimateService.isAutoAcceptEnabled
            getSharedPreferences("jeeny_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("auto_accept", JeenyUltimateService.isAutoAcceptEnabled)
                .apply()
            updateToggleUI(toggleBtn, statusText)
        }

        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    wm.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }

        wm.addView(overlayView, params)

        JeenyUltimateService.onStatsUpdated = {
            updateToggleUI(toggleBtn, statusText)
        }
    }

    private fun updateToggleUI(btn: ImageButton, statusText: TextView) {
        val isEnabled = JeenyUltimateService.isAutoAcceptEnabled
        btn.setImageResource(if (isEnabled) R.drawable.ic_status_on else R.drawable.ic_status_off)
        statusText.text = if (isEnabled) "✓ ${JeenyUltimateService.countAccepted}" else "✗"
        overlayView.alpha = if (isEnabled) 1.0f else 0.6f
    }

    override fun onDestroy() {
        super.onDestroy()
        JeenyUltimateService.onStatsUpdated = null
        if (::overlayView.isInitialized) wm.removeView(overlayView)
    }
}
