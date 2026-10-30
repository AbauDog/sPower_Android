package com.example.spower

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class SPowerDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "sPower 裝置管理員已啟用，可以關閉螢幕", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "sPower 裝置管理員已停用，無法再關閉螢幕", Toast.LENGTH_SHORT).show()
    }
}
