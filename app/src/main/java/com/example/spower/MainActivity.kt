package com.example.spower

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var audioManager: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, SPowerDeviceAdminReceiver::class.java)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        setContent {
            MaterialTheme {
                Surface {
                    SPowerScreen(
                        canWriteSettings = ::canWriteSettings,
                        onRequestWriteSettings = ::requestWriteSettingsPermission,
                        getBrightness = ::getBrightness,
                        onSetBrightness = ::setBrightness,
                        onSetAutoBrightness = ::setAutoBrightness,
                        isDeviceAdminActive = { devicePolicyManager.isAdminActive(adminComponent) },
                        onRequestDeviceAdmin = ::requestDeviceAdmin,
                        onTurnOffScreen = ::turnOffScreen,
                        onTurnOnScreen = ::turnOnScreen,
                        getVolume = { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) },
                        getMaxVolume = { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) },
                        onSetVolume = ::setVolume,
                        onAdjustVolume = ::adjustVolume
                    )
                }
            }
        }
    }

    // ---------- 螢幕亮度 ----------

    // API 23 以下沒有「修改系統設定」特殊授權機制，WRITE_SETTINGS 在安裝時就已生效
    private fun canWriteSettings(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(this)
        } else {
            true
        }
    }

    private fun requestWriteSettingsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun getBrightness(): Int {
        return try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Settings.SettingNotFoundException) {
            128
        }
    }

    private fun setBrightness(value: Int) {
        if (!canWriteSettings()) {
            Toast.makeText(this, "請先授權「修改系統設定」權限", Toast.LENGTH_SHORT).show()
            requestWriteSettingsPermission()
            return
        }
        Settings.System.putInt(
            contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
    }

    private fun setAutoBrightness() {
        if (!canWriteSettings()) {
            Toast.makeText(this, "請先授權「修改系統設定」權限", Toast.LENGTH_SHORT).show()
            requestWriteSettingsPermission()
            return
        }
        Settings.System.putInt(
            contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        )
    }

    // ---------- 螢幕開關 ----------

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "啟用後 sPower 才能呼叫「鎖定螢幕」來真正關閉螢幕顯示"
            )
        }
        startActivity(intent)
    }

    private fun turnOffScreen() {
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            Toast.makeText(this, "請先啟用裝置管理員權限才能關閉螢幕", Toast.LENGTH_SHORT).show()
            requestDeviceAdmin()
            return
        }
        devicePolicyManager.lockNow()
    }

    @Suppress("DEPRECATION")
    private fun turnOnScreen() {
        // 用舊版 window flags（API 1 起就有）取代 API 27 的 setTurnScreenOn/setShowWhenLocked，
        // 才能相容這台 Android 5.1 (API 22) 測試機；新版系統上仍持續有效，只是被標記為已淘汰。
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        Toast.makeText(
            this,
            "若螢幕已完全關閉，App 不在前景時請改按實體電源鍵喚醒",
            Toast.LENGTH_LONG
        ).show()
    }

    // ---------- 音量 ----------

    private fun setVolume(value: Int) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
    }

    private fun adjustVolume(raise: Boolean) {
        val direction = if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }
}

private val compactButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
private val adminGreen = Color(0xFF2E7D32)

@Composable
fun SPowerScreen(
    canWriteSettings: () -> Boolean,
    onRequestWriteSettings: () -> Unit,
    getBrightness: () -> Int,
    onSetBrightness: (Int) -> Unit,
    onSetAutoBrightness: () -> Unit,
    isDeviceAdminActive: () -> Boolean,
    onRequestDeviceAdmin: () -> Unit,
    onTurnOffScreen: () -> Unit,
    onTurnOnScreen: () -> Unit,
    getVolume: () -> Int,
    getMaxVolume: () -> Int,
    onSetVolume: (Int) -> Unit,
    onAdjustVolume: (Boolean) -> Unit
) {
    var brightness by remember { mutableIntStateOf(getBrightness()) }
    var volume by remember { mutableIntStateOf(getVolume()) }
    val maxVolume = remember { getMaxVolume() }
    val adminActive = isDeviceAdminActive()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("sPower 螢幕控制", style = MaterialTheme.typography.titleMedium)

        Text("亮度", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = brightness.toFloat(),
            valueRange = 0f..255f,
            onValueChange = {
                brightness = it.toInt()
                if (canWriteSettings()) onSetBrightness(brightness) else onRequestWriteSettings()
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(contentPadding = compactButtonPadding, onClick = {
                brightness = 255
                if (canWriteSettings()) onSetBrightness(255) else onRequestWriteSettings()
            }) { Text("最亮") }
            Button(contentPadding = compactButtonPadding, onClick = {
                if (canWriteSettings()) onSetAutoBrightness() else onRequestWriteSettings()
            }) { Text("AUTO") }
            Button(contentPadding = compactButtonPadding, onClick = {
                brightness = 0
                if (canWriteSettings()) onSetBrightness(0) else onRequestWriteSettings()
            }) { Text("最暗") }
        }

        Text("音量", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = volume.toFloat(),
            valueRange = 0f..maxVolume.toFloat(),
            steps = if (maxVolume > 1) maxVolume - 1 else 0,
            onValueChange = {
                volume = it.toInt()
                onSetVolume(volume)
            }
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(contentPadding = compactButtonPadding, onClick = {
                onAdjustVolume(false)
                volume = getVolume()
            }) { Text("-") }
            Text("$volume / $maxVolume")
            Button(contentPadding = compactButtonPadding, onClick = {
                onAdjustVolume(true)
                volume = getVolume()
            }) { Text("+") }
        }

        Text("螢幕", style = MaterialTheme.typography.labelLarge)
        if (!adminActive) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("尚未啟用裝置管理員", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = onRequestDeviceAdmin,
                    colors = ButtonDefaults.buttonColors(containerColor = adminGreen),
                    contentPadding = compactButtonPadding
                ) { Text("啟用裝置管理員") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(contentPadding = compactButtonPadding, onClick = onTurnOffScreen) { Text("關閉螢幕") }
            Button(contentPadding = compactButtonPadding, onClick = onTurnOnScreen) { Text("開啟螢幕") }
        }
    }
}
