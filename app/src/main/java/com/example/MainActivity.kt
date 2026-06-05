package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val capturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, FloatingWidgetService::class.java).apply {
                putExtra(FloatingWidgetService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(FloatingWidgetService.EXTRA_PROJECTION_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            finish() // Minimize config page so user can see overlays over the Clash of Clans app
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Overlay permission update
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setContent {
            var hasOverlayPermission by remember { mutableStateOf(false) }
            var isAccessibilityConnected by remember { mutableStateOf(false) }

            // Check permissions periodically on resume
            DisposableEffect(Unit) {
                val thread = Thread {
                    while (true) {
                        hasOverlayPermission = Settings.canDrawOverlays(this@MainActivity)
                        isAccessibilityConnected = AutoClickService.isConnected
                        try {
                            Thread.sleep(1000)
                        } catch (e: InterruptedException) {
                            break
                        }
                    }
                }
                thread.start()
                onDispose {
                    thread.interrupt()
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFFAB00), // Gold CoC theme
                    secondary = Color(0xFFE040FB), // Elixir theme
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onSurface = Color(0xFFE0E0E0)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF260D0A), Color(0xFF121212))
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Title header
                        Text(
                            text = "AUTONEXT CoC",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFD700),
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Text(
                            text = "Autonomous Clash of Clans Loot Auto-Scanner",
                            fontSize = 12.sp,
                            color = Color(0xFFB0B0B0),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 32.dp)
                        )

                        // Config Status Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "System Setup Controls",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                // Overlay permission row
                                PermissionIndicatorRow(
                                    name = "1. Screen Overlays",
                                    isGranted = hasOverlayPermission,
                                    actionLabel = "Grant",
                                    onAction = {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:$packageName")
                                        )
                                        overlayPermissionLauncher.launch(intent)
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Accessibility clicker service row
                                PermissionIndicatorRow(
                                    name = "2. Click Service",
                                    isGranted = isAccessibilityConnected,
                                    actionLabel = "Enable",
                                    onAction = {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        startActivity(intent)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Launch/Start Button
                        Button(
                            onClick = {
                                requestProjectionCapture()
                            },
                            enabled = hasOverlayPermission && isAccessibilityConnected,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50),
                                disabledContainerColor = Color(0xFF2C4C2E)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text(
                                text = "LAUNCH OVERLAY",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Ensure Clash of Clans is loaded on screen, positioning the scanning red box over top-left resources and click-target over the 'Next' button directly.",
                            fontSize = 11.sp,
                            color = Color(0xFF888888),
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }

    private fun requestProjectionCapture() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        capturePermissionLauncher.launch(captureIntent)
    }
}

@Composable
fun PermissionIndicatorRow(
    name: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = name,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isGranted) "Authorized" else "Awaiting action",
                fontSize = 11.sp,
                color = if (isGranted) Color(0xFF4CAF50) else Color(0xFFFF8A80)
            )
        }

        if (isGranted) {
            Button(
                onClick = {},
                enabled = false,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = Color(0xFF152918),
                    disabledContentColor = Color(0xFF4CAF50)
                ),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("OK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFAB00)),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}
