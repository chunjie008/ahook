package com.wzh.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * MQTT 前台服务
 * 用于保持 MQTT 连接在后台持续运行
 */
class MqttForegroundService : Service() {
    
    companion object {
        private const val TAG = "MqttForegroundService"
        private const val CHANNEL_ID = "mqtt_service_channel"
        private const val NOTIFICATION_ID = 1001
        
        private var isRunning = false
        
        fun isServiceRunning(): Boolean = isRunning
        
        fun start(context: Context) {
            val intent = Intent(context, MqttForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, MqttForegroundService::class.java)
            context.stopService(intent)
        }
    }
    
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MQTT 前台服务创建")
        createNotificationChannel()
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "MQTT 前台服务启动")
        isRunning = true
        
        // 启动前台通知
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // 初始化 MQTT 连接
        val prefs = PreferencesUtil(this)
        if (prefs.isMqttEnabled()) {
            MQTTManager.getInstance().initialize(this)
        }
        
        // 返回 START_STICKY 确保服务被杀后自动重启
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MQTT 前台服务销毁")
        isRunning = false
        releaseWakeLock()
        MQTTManager.getInstance().disconnect()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MQTT 服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持 MQTT 连接活跃"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        // 点击通知打开应用
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AHOOK MQTT 服务")
            .setContentText("正在监控加密操作并推送日志...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AHOOK::MqttWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 分钟超时，会自动续期
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    /**
     * 更新通知内容
     */
    fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AHOOK MQTT 服务")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
