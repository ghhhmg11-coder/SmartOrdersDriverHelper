package com.smartorders.driverhelper.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.smartorders.driverhelper.data.AppState
import com.smartorders.driverhelper.data.LogType
import com.smartorders.driverhelper.ui.theme.AccentGreen
import com.smartorders.driverhelper.ui.theme.AccentPurple
import com.smartorders.driverhelper.ui.theme.AccentRed
import com.smartorders.driverhelper.ui.theme.DarkCard
import com.smartorders.driverhelper.ui.theme.SmartOrdersTheme

class FloatingOverlayService : Service(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var floatingView: FrameLayout? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    companion object {
        private const val CHANNEL_ID = "smart_orders_overlay"
        private const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        showFloatingButton()
        AppState.addLog("Floating overlay started", LogType.INFO)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        return START_STICKY
    }

    override fun onDestroy() {
        AppState.addLog("Floating overlay stopped", LogType.WARNING)
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        windowManager?.removeView(floatingView)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showFloatingButton() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setContent {
                SmartOrdersTheme {
                    FloatingPanel(
                        onForceAccept = {
                            val svc = JeenyAccessibilityService()
                            // Gesture tap via accessibility service
                            performForceAcceptGesture()
                        }
                    )
                }
            }
        }

        val container = FrameLayout(this)
        container.addView(composeView)

        floatingView = container

        var isDragging = false
        var startX = 0f
        var startY = 0f

        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(floatingView, params)
    }

    private fun performForceAcceptGesture() {
        // Send broadcast to accessibility service to force tap
        val intent = Intent("com.smartorders.driverhelper.FORCE_ACCEPT")
        sendBroadcast(intent)
        AppState.addLog("⚡ Force Accept gesture triggered", LogType.WARNING)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Smart Orders Overlay",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Orders")
            .setContentText("Monitoring Jeeny Driver")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

@Composable
fun FloatingPanel(onForceAccept: () -> Unit) {
    var showPanel by remember { mutableStateOf(false) }
    val isOn by AppState.isAutoAcceptEnabled

    Column(horizontalAlignment = Alignment.Start) {
        // Main floating button
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isOn) AccentGreen else AccentPurple)
                .border(2.dp, ComposeColor.White.copy(alpha = 0.3f), CircleShape)
                .clickable { showPanel = !showPanel },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isOn) "ON" else "OFF",
                color = ComposeColor.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        // Expanded control panel
        if (showPanel) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .width(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard)
                    .border(1.dp, AccentPurple.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Smart Orders",
                    color = AccentPurple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                // START
                Button(
                    onClick = {
                        AppState.isAutoAcceptEnabled.value = true
                        AppState.addLog("▶ Auto-accept ENABLED", LogType.SUCCESS)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("▶  START", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                // STOP
                Button(
                    onClick = {
                        AppState.isAutoAcceptEnabled.value = false
                        AppState.addLog("⏹ Auto-accept DISABLED", LogType.WARNING)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("■  STOP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                // FORCE ACCEPT TEST
                Button(
                    onClick = onForceAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("⚡ FORCE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
