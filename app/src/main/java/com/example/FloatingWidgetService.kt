package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class FloatingWidgetService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences

    private var controlPanel: View? = null
    private var scannerBox: View? = null
    private var clickPointer: View? = null

    private var controlPanelParams = WindowManager.LayoutParams()
    private var scannerBoxParams = WindowManager.LayoutParams()
    private var clickPointerParams = WindowManager.LayoutParams()

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionCallback: MediaProjection.Callback? = null

    private var scanScope: CoroutineScope? = null
    private var isScanning = false

    private var minGoldThreshold = 450000L
    private var minElixirThreshold = 450000L

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingWidgetService Created")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("autonext_prefs", Context.MODE_PRIVATE)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Read saved thresholds
        minGoldThreshold = prefs.getLong("min_gold", 450000L)
        minElixirThreshold = prefs.getLong("min_elixir", 450000L)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Service started"))

        setupOverlays()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val data = intent.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)
            if (resultCode != 0 && data != null) {
                setupMediaProjection(resultCode, data)
            }
        }
        return START_NOT_STICKY
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        mediaProjection?.stop()
        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
        Log.d(TAG, "MediaProjection successfully obtained: $mediaProjection")
    }

    private fun setupOverlays() {
        val inflater = LayoutInflater.from(this)

        // Common window params helper
        fun createDefaultParams(w: Int, h: Int, x: Int, y: Int): WindowManager.LayoutParams {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            return WindowManager.LayoutParams(
                w, h, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                this.x = x
                this.y = y
            }
        }

        // 1. Core control panel
        controlPanel = inflater.inflate(R.layout.view_control_panel, null)
        val panelX = prefs.getInt("panel_x", 100)
        val panelY = prefs.getInt("panel_y", 200)
        controlPanelParams = createDefaultParams(
            dpToPx(160), WindowManager.LayoutParams.WRAP_CONTENT, panelX, panelY
        )
        windowManager.addView(controlPanel, controlPanelParams)

        // 2. Scanner box
        scannerBox = inflater.inflate(R.layout.view_scanner_box, null)
        val scanX = prefs.getInt("scan_x", 30)
        val scanY = prefs.getInt("scan_y", 150)
        scannerBoxParams = createDefaultParams(dpToPx(160), dpToPx(90), scanX, scanY)
        windowManager.addView(scannerBox, scannerBoxParams)

        // 3. Simulated clicking pointer target
        clickPointer = inflater.inflate(R.layout.view_click_pointer, null)
        val targetX = prefs.getInt("click_x", 850)
        val targetY = prefs.getInt("click_y", 500)
        clickPointerParams = createDefaultParams(dpToPx(50), dpToPx(50), targetX, targetY)
        windowManager.addView(clickPointer, clickPointerParams)

        // Set dragging mechanics on overlay views
        makeDraggable(controlPanel!!, controlPanelParams, "panel")
        makeDraggable(scannerBox!!, scannerBoxParams, "scan")
        makeDraggable(clickPointer!!, clickPointerParams, "click")

        // Populate panel values and events
        updatePanelUi()

        controlPanel?.findViewById<Button>(R.id.btn_close)?.setOnClickListener {
            stopSelf()
        }

        controlPanel?.findViewById<Button>(R.id.btn_gold_minus)?.setOnClickListener {
            if (minGoldThreshold > 50000L) {
                minGoldThreshold -= 50000L
                saveThresholds()
                updatePanelUi()
            }
        }
        controlPanel?.findViewById<Button>(R.id.btn_gold_plus)?.setOnClickListener {
            if (minGoldThreshold < 2000000L) {
                minGoldThreshold += 50000L
                saveThresholds()
                updatePanelUi()
            }
        }

        controlPanel?.findViewById<Button>(R.id.btn_elixir_minus)?.setOnClickListener {
            if (minElixirThreshold > 50000L) {
                minElixirThreshold -= 50000L
                saveThresholds()
                updatePanelUi()
            }
        }
        controlPanel?.findViewById<Button>(R.id.btn_elixir_plus)?.setOnClickListener {
            if (minElixirThreshold < 2000000L) {
                minElixirThreshold += 50000L
                saveThresholds()
                updatePanelUi()
            }
        }

        controlPanel?.findViewById<Button>(R.id.btn_start_stop)?.setOnClickListener {
            toggleScanning()
        }
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams, prefPrefix: String) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(view, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // Persist position indices
                        prefs.edit().apply {
                            putInt("${prefPrefix}_x", params.x)
                            putInt("${prefPrefix}_y", params.y)
                            apply()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun updatePanelUi() {
        controlPanel?.findViewById<TextView>(R.id.tv_gold_val)?.text = formatLoot(minGoldThreshold)
        controlPanel?.findViewById<TextView>(R.id.tv_elixir_val)?.text = formatLoot(minElixirThreshold)
    }

    private fun formatLoot(amount: Long): String {
        return if (amount >= 1000000L) {
            val millions = amount.toDouble() / 1000000.0
            String.format("%.1fM", millions)
        } else if (amount >= 1000L) {
            "${amount / 1000}K"
        } else {
            amount.toString()
        }
    }

    private fun toggleScanning() {
        if (isScanning) {
            stopScanningLoop()
        } else {
            if (mediaProjection == null) {
                controlPanel?.findViewById<TextView>(R.id.tv_status_text)?.apply {
                    text = "Requires screen capture auth!"
                    setTextColor(0xFFFF3F3F.toInt())
                }
                return
            }
            if (!AutoClickService.isConnected) {
                controlPanel?.findViewById<TextView>(R.id.tv_status_text)?.apply {
                    text = "Enable Accessibility Clicker!"
                    setTextColor(0xFFFF3F3F.toInt())
                }
                return
            }
            startScanningLoop()
        }
    }

    private fun startScanningLoop() {
        isScanning = true
        controlPanel?.findViewById<Button>(R.id.btn_start_stop)?.text = "STOP"
        controlPanel?.findViewById<TextView>(R.id.tv_status_text)?.apply {
            text = "Scanning..."
            setTextColor(0xFF4CAF50.toInt())
        }

        scanScope = CoroutineScope(Dispatchers.Default + Job())
        scanScope?.launch {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            // Setup ImageReader for screen grab if not already setup
            if (imageReader == null) {
                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            }
            if (virtualDisplay == null) {
                try {
                    val projection = mediaProjection ?: return@launch
                    virtualDisplay = projection.createVirtualDisplay(
                        "AutoNextCoCVirtual",
                        width, height, density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader?.surface, null, null
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create virtualDisplay", e)
                    withContext(Dispatchers.Main) {
                        controlPanel?.findViewById<TextView>(R.id.tv_status_text)?.text = "Error: Display capture failed!"
                        isScanning = false
                        controlPanel?.findViewById<Button>(R.id.btn_start_stop)?.text = "START"
                    }
                    return@launch
                }
            }

            while (isScanning) {
                try {
                    // Capture layout coordinates before we hide them
                    val scanX = scannerBoxParams.x
                    val scanY = scannerBoxParams.y
                    val scanW = scannerBoxParams.width
                    val scanH = scannerBoxParams.height

                    val tapX = clickPointerParams.x + (clickPointerParams.width / 2f)
                    val tapY = clickPointerParams.y + (clickPointerParams.height / 2f)

                    // 1. Temporarily hide overlays to perform full clear screen grab
                    withContext(Dispatchers.Main) {
                        scannerBox?.visibility = View.INVISIBLE
                        clickPointer?.visibility = View.INVISIBLE
                    }

                    // Await view layouts to settle
                    delay(350)

                    val fullBitmap = getLatestBitmap()

                    // 2. Unhide overlays immediately
                    withContext(Dispatchers.Main) {
                        scannerBox?.visibility = View.VISIBLE
                        clickPointer?.visibility = View.VISIBLE
                    }

                    if (fullBitmap != null) {
                        // Ensure cropping rectangle coordinates lie cleanly within bitmap boundaries to avoid OOB error
                        val cropX = scanX.coerceIn(0, fullBitmap.width - 2)
                        val cropY = scanY.coerceIn(0, fullBitmap.height - 2)
                        val cropW = scanW.coerceAtMost(fullBitmap.width - cropX)
                        val cropH = scanH.coerceAtMost(fullBitmap.height - cropY)

                        var croppedBitmap: Bitmap? = null
                        if (cropW > 0 && cropH > 0) {
                            croppedBitmap = Bitmap.createBitmap(fullBitmap, cropX, cropY, cropW, cropH)
                        }

                        if (croppedBitmap != null) {
                            val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
                            
                            // Process text recognition
                            val textResult = suspendCancellableCoroutine<String> { continuation ->
                                recognizer.process(inputImage)
                                    .addOnSuccessListener { visionText ->
                                        continuation.resume(visionText.text)
                                    }
                                    .addOnFailureListener {
                                        continuation.resume("")
                                    }
                            }

                            val (gold, elixir) = LootEvaluator.parseLoot(textResult)

                            withContext(Dispatchers.Main) {
                                val displayText = if (gold == 0L && elixir == 0L) {
                                    val cleanedRaw = textResult.trim().replace("\n", " ")
                                    if (cleanedRaw.isEmpty()) {
                                        "No loot detected (check scanner)"
                                    } else {
                                        "Read: 0 | Raw: \"$cleanedRaw\""
                                    }
                                } else {
                                    "G: ${formatLoot(gold)} | E: ${formatLoot(elixir)}"
                                }
                                controlPanel?.findViewById<TextView>(R.id.tv_ocr_preview)?.text = displayText
                            }

                            // Evaluate criteria
                            if (gold >= minGoldThreshold || elixir >= minElixirThreshold) {
                                withContext(Dispatchers.Main) {
                                    controlPanel?.findViewById<TextView>(R.id.tv_status_text)?.apply {
                                        text = "Target Loot Found!"
                                        setTextColor(0xFF4CAF50.toInt())
                                    }
                                    isScanning = false
                                    controlPanel?.findViewById<Button>(R.id.btn_start_stop)?.text = "START"
                                }
                                break
                            } else {
                                // Tap next
                                withContext(Dispatchers.Main) {
                                    val goldStr = formatLoot(gold)
                                    val elixirStr = formatLoot(elixir)
                                    controlPanel?.findViewById<TextView>(R.id.tv_status_text)?.text = "G: $goldStr, E: $elixirStr (Low) - Next..."
                                }
                                AutoClickService.tapAt(tapX, tapY)
                                // Await animations, loading page, next village searching... (lasts about 3.5 to 5.0 seconds standard)
                                delay(4200)
                            }
                        }
                    } else {
                        Log.e(TAG, "Screen capture returned null bitmap")
                        delay(1000)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Exception during scanning iteration", e)
                    delay(1000)
                }
            }
        }
    }

    private fun getLatestBitmap(): Bitmap? {
        val reader = imageReader ?: return null
        var image: Image? = null
        try {
            image = reader.acquireLatestImage() ?: return null
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            
            return if (rowPadding > 0) {
                Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading bitmap from image reader", e)
        } finally {
            image?.close()
        }
        return null
    }

    private fun stopScanningLoop() {
        isScanning = false
        controlPanel?.findViewById<Button>(R.id.btn_start_stop)?.text = "START"
        controlPanel?.findViewById<TextView>(R.id.tv_status_text)?.apply {
            text = "Stopped"
            setTextColor(0xFFFF3F3F.toInt())
        }
        scanScope?.cancel()
        scanScope = null
        
        // Ensure overlays are visible again
        scannerBox?.visibility = View.VISIBLE
        clickPointer?.visibility = View.VISIBLE
    }

    private fun saveThresholds() {
        prefs.edit().apply {
            putLong("min_gold", minGoldThreshold)
            putLong("min_elixir", minElixirThreshold)
            apply()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoNext Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors and automates Clash of Clans screen overlays and clicks."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoNext CoC Service")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    private fun registerProjectionCallback(projection: MediaProjection) {
        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.d(TAG, "MediaProjection stopped by system")
                stopScanningLoop()
                mediaProjection = null
            }
        }
        projection.registerCallback(projectionCallback!!, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FloatingWidgetService Destroyed")
        stopScanningLoop()

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing virtualDisplay", e)
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing imageReader", e)
        }
        imageReader = null

        try {
            projectionCallback?.let {
                mediaProjection?.unregisterCallback(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering projection callback", e)
        }
        projectionCallback = null

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mediaProjection", e)
        }
        mediaProjection = null

        recognizer.close()

        // Remove overlay elements from layout dynamically
        controlPanel?.let { windowManager.removeView(it) }
        scannerBox?.let { windowManager.removeView(it) }
        clickPointer?.let { windowManager.removeView(it) }
    }

    companion object {
        private const val TAG = "FloatingWidgetService"
        private const val CHANNEL_ID = "Autonext_Service_Channel_ID"
        private const val NOTIFICATION_ID = 13579

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"
    }
}
