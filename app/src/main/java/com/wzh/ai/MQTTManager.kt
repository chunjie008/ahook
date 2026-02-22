package com.wzh.ai

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.*

class MQTTManager private constructor() {
    private var mqttClient: MqttAsyncClient? = null
    private var isConnected = false
    private var context: Context? = null
    
    companion object {
        private const val TAG = "MQTTManager"
        private const val CLIENT_ID_PREFIX = "ahook_client_"
        
        @Volatile
        private var INSTANCE: MQTTManager? = null
        
        fun getInstance(): MQTTManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MQTTManager().also { INSTANCE = it }
            }
        }
    }
    
    fun initialize(context: Context) {
        this.context = context.applicationContext
        connect()
    }
    
    private fun connect() {
        try {
            // 更新配置
            context?.let { MQTTConfig.setConfig(it) }
            
            val clientId = CLIENT_ID_PREFIX + UUID.randomUUID().toString()
            // 使用纯 Java MQTT 客户端，避免 Android Service 兼容性问题
            mqttClient = MqttAsyncClient(MQTTConfig.getBrokerUrl(), clientId, MemoryPersistence())
            
            // 设置回调
            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.w(TAG, "MQTT连接断开", cause)
                    isConnected = false
                    scheduleReconnect()
                }
                
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    Log.d(TAG, "收到消息: $topic -> ${message?.toString()}")
                }
                
                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    // 消息发送完成
                }
            })
            
            val options = MqttConnectOptions().apply {
                isCleanSession = MQTTConfig.CLEAN_SESSION
                connectionTimeout = MQTTConfig.CONNECTION_TIMEOUT
                keepAliveInterval = MQTTConfig.KEEP_ALIVE_INTERVAL
                isAutomaticReconnect = true
                // 如果需要认证，设置用户名密码
                if (MQTTConfig.getUsername().isNotEmpty()) {
                    userName = MQTTConfig.getUsername()
                    password = MQTTConfig.getPassword().toCharArray()
                }
            }
            
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "MQTT连接成功")
                    isConnected = true
                    // 连接成功后可以订阅相关主题（如果需要接收消息）
                    subscribeToTopics()
                }
                
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "MQTT连接失败", exception)
                    isConnected = false
                    // 失败后尝试重连
                    scheduleReconnect()
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "MQTT初始化异常", e)
        }
    }
    
    private fun subscribeToTopics() {
        try {
            // 订阅控制命令主题（可选）
            mqttClient?.subscribe(MQTTConfig.CONTROL_TOPIC, MQTTConfig.DEFAULT_QOS, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "订阅控制主题成功")
                }
                
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "订阅控制主题失败", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "订阅主题异常", e)
        }
    }
    
    fun sendLog(logItem: LogItem) {
        if (!isConnected) {
            Log.w(TAG, "MQTT未连接，无法发送日志")
            return
        }
        
        try {
            val json = JSONObject().apply {
                put("timestamp", logItem.timestamp)
                put("logName", logItem.logName)
                put("packageName", logItem.packageName)
                put("appName", logItem.appName)
                put("keyType", logItem.keyType)
                put("keyString", logItem.keyString)
                put("keyHex", logItem.keyHex)
                put("keyBase64", logItem.keyBase64)
                put("ivString", logItem.ivString)
                put("ivHex", logItem.ivHex)
                put("ivBase64", logItem.ivBase64)
                put("inputString", logItem.inputString)
                put("inputHex", logItem.inputHex)
                put("inputBase64", logItem.inputBase64)
                put("outputString", logItem.outputString)
                put("outputHex", logItem.outputHex)
                put("outputBase64", logItem.outputBase64)
                put("stackTrace", logItem.stackTrace)
            }
            
            // 统一主题，消息中已包含 packageName 字段，无需按包名分类
            val topic = MQTTConfig.getLogTopicPrefix() + "all"
            val message = MqttMessage(json.toString().toByteArray()).apply {
                qos = MQTTConfig.DEFAULT_QOS
                isRetained = false
            }
            
            mqttClient?.publish(topic, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "日志发送成功: ${logItem.logName}")
                }
                
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "日志发送失败: ${logItem.logName}", exception)
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "发送日志异常", e)
        }
    }
    
    fun sendBatchLogs(logItems: List<LogItem>) {
        logItems.forEach { sendLog(it) }
    }
    
    private fun scheduleReconnect() {
        // 简单的重连机制，实际项目中建议使用更完善的重连策略
        Thread {
            try {
                Thread.sleep(MQTTConfig.RECONNECT_DELAY)
                if (!isConnected) {
                    connect()
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.start()
    }
    
    fun disconnect() {
        try {
            mqttClient?.disconnect(null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "MQTT断开连接成功")
                    isConnected = false
                }
                
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "MQTT断开连接失败", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "MQTT断开异常", e)
        }
    }
    
    fun isConnected(): Boolean = isConnected
}