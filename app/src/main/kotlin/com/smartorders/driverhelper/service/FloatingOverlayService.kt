package com.smartorders.driverhelper.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.smartorders.driverhelper.AppState
import com.smartorders.driverhelper.PreferencesManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class FloatingOverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "smart_orders_overlay"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingOverlayService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var panelView: View? = null
    private var panelShown = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var lastX = 0
    private var lastY = 0
    private var startX = 0
    private var startY = 0
    private var isDragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingButton()
        observeState()
    }

    private fun createFloatingButton() {
        val wlpType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            wlpType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        val button = FrameLayout(this).apply {
            val size = (56 * resources.displayMetrics.density).toInt()
            val circleView = View(context).apply {
                setBackgroundColor(Color.parseColor("#9C27B0"))
                layoutParams = FrameLayout.LayoutParams(size, size).also { it.gravity = Gravity.CENTER }
                background = resources.getDrawable(
                    android.R.drawable.presence_busy, null
                )
            }

            val label = TextView(context).apply {
                text = "ON"
                setTextColor(Color.WHITE)
                textSize = 11f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(size, size).also {
                    it.gravity = Gravity.CENTER
                }
            }

            setPadding(4, 4, 4, 4)
            setBackgroundColor(Color.parseColor("#CC9C27B0"))

            val radius = size.toFloat() / 2f
            clipToOutline = false

            addView(createCircleView(size, label.text.toString()))
        }

        val overlayContainer = createOverlayBubble()

        overlayContainer.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = params.x
                        lastY = params.y
                        startX = event.rawX.toInt()
                        startY = event.rawY.toInt()
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX.toInt() - startX
                        val dy = event.rawY.toInt() - startY
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true
                        if (isDragging) {
                            params.x = lastX + dx
                            params.y = lastY + dy
                            windowManager.updateViewLayout(overlayContainer, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            togglePanel(params)
                        }
                        return true
                    }
                }
                return false
            }
        })

        floatingView = overlayContainer
        windowManager.addView(overlayContainer, params)
    }

    private fun createOverlayBubble(): LinearLayout {
        val density = resources.displayMetrics.density
        val size = (56 * density).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            val bgView = TextView(this@FloatingOverlayService).apply {
                val autoEnabled = PreferencesManager.isAutoAcceptEnabled(this@FloatingOverlayService)
                text = if (autoEnabled) "ON" else "OFF"
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor(if (autoEnabled) "#9C27B0" else "#555555"))
                setPadding(12, 8, 12, 8)
            }

            addView(bgView, LinearLayout.LayoutParams(size, size))
        }
    }

    private fun createCircleView(size: Int, label: String): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#9C27B0"))
            layoutParams = FrameLayout.LayoutParams(size, size)
        }
    }

    private fun togglePanel(bubbleParams: WindowManager.LayoutParams) {
        if (panelShown) {
            hidePanel()
        } else {
            showPanel(bubbleParams)
        }
    }

    private fun showPanel(bubbleParams: WindowManager.LayoutParams) {
        if (panelShown) return
        val density = resources.displayMetrics.density

        val wlpType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val panelParams = WindowManager.LayoutParams(
            (260 * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            wlpType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleParams.x + (70 * density).toInt()
            y = bubbleParams.y
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#DD1A1D27"))
            setPadding(16, 16, 16, 16)

            val titleView = TextView(this@FloatingOverlayService).apply {
                text = "Smart Orders"
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 12)
            }
            addView(titleView)

            val startBtn = createPanelButton("▶ START", "#4CAF50") {
                PreferencesManager.setAutoAcceptEnabled(this@FloatingOverlayService, true)
                AppState.setAutoAcceptEnabled(true)
                AppState.addEventLog("Auto-accept ENABLED ▶")
                sendBroadcast(Intent(JeenyAccessibilityService.ACTION_TOGGLE_AUTO_ACCEPT).apply {
                    putExtra(JeenyAccessibilityService.EXTRA_ENABLED, true)
                })
                updateBubbleLabel(true)
                hidePanel()
            }
            addView(startBtn)

            addView(createSpacerView(8))

            val stopBtn = createPanelButton("⏸ STOP", "#E53935") {
                PreferencesManager.setAutoAcceptEnabled(this@FloatingOverlayService, false)
                AppState.setAutoAcceptEnabled(false)
                AppState.addEventLog("Auto-accept DISABLED ⏸")
                sendBroadcast(Intent(JeenyAccessibilityService.ACTION_TOGGLE_AUTO_ACCEPT).apply {
                    putExtra(JeenyAccessibilityService.EXTRA_ENABLED, false)
                })
                updateBubbleLabel(false)
                hidePanel()
            }
            addView(stopBtn)

            addView(createSpacerView(8))

            val forceBtn = createPanelButton("🔴 FORCE ACCEPT TEST", "#FF6F00") {
                AppState.addEventLog("Force Accept triggered from panel")
                sendBroadcast(Intent(JeenyAccessibilityService.ACTION_FORCE_ACCEPT))
                hidePanel()
            }
            addView(forceBtn)

            addView(createSpacerView(4))

            val closeBtn = createPanelButton("✕ Close", "#555555") {
                hidePanel()
            }
            addView(closeBtn)
        }

        panelView = panel
        panelShown = true
        windowManager.addView(panel, panelParams)
    }

    private fun createPanelButton(text: String, colorHex: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor(colorHex))
            setPadding(16, 12, 16, 12)
            setOnClickListener { onClick() }
        }
    }

    private fun createSpacerView(dpHeight: Int): View {
        return View(this).apply {
            val px = (dpHeight * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px
            )
        }
    }

    private fun hidePanel() {
        panelView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        panelView = null
        panelShown = false
    }

    private fun updateBubbleLabel(isOn: Boolean) {
        serviceScope.launch {
            try {
                val container = floatingView as? LinearLayout ?: return@launch
                val label = container.getChildAt(0) as? TextView ?: return@launch
                label.text = if (isOn) "ON" else "OFF"
                label.setBackgroundColor(
                    Color.parseColor(if (isOn) "#9C27B0" else "#555555")
                )
            } catch (_: Exception) {}
        }
    }

    private fun observeState() {
        serviceScope.launch {
            AppState.autoAcceptEnabled.collectLatest { enabled ->
                updateBubbleLabel(enabled)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smart Orders Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating overlay for Smart Orders Driver Helper"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Smart Orders Active")
                .setContentText("Monitoring Jeeny Driver")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Smart Orders Active")
                .setContentText("Monitoring Jeeny Driver")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        hidePanel()
        floatingView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
    }
}
