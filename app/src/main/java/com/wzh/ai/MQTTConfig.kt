package com.wzh.ai

import android.content.Context

object MQTTConfig {
    // MQTT Broker配置 - 这些值将从PreferencesUtil动态获取
    private var brokerUrl: String = "tcp://192.168.50.18:1883"  // 替换为你的MQTT服务器地址
    private var username: String = ""  // 如果需要认证，填写用户名
    private var password: String = ""  // 如果需要认证，填写密码
    
    // 主题配置
    private var logTopicPrefix: String = "ahook/logs/"      // 日志主题前缀
    
    // 静态配置
    const val CONNECTION_TIMEOUT = 30  // 连接超时时间(秒)
    const val KEEP_ALIVE_INTERVAL = 60  // 心跳间隔(秒)
    const val CLEAN_SESSION = true      // 是否清除会话
    const val CONTROL_TOPIC = "ahook/control/#"     // 控制命令主题
    const val DEFAULT_QOS = 1  // 默认服务质量等级 (0:最多一次, 1:至少一次, 2:恰好一次)
    
    // 重连配置
    const val RECONNECT_DELAY = 5000L   // 重连延迟(毫秒)
    const val MAX_RECONNECT_ATTEMPTS = 5  // 最大重连次数
    
    // 批量发送配置
    const val BATCH_SIZE = 10           // 批量发送大小
    const val BATCH_DELAY = 1000L       // 批量发送延迟(毫秒)
    
    // 获取配置值的方法
    fun getBrokerUrl(): String = brokerUrl
    fun getUsername(): String = username
    fun getPassword(): String = password
    fun getLogTopicPrefix(): String = logTopicPrefix
    
    // 设置配置值的方法
    fun setConfig(context: Context) {
        val prefs = PreferencesUtil(context)
        brokerUrl = prefs.getMqttBrokerUrl()
        username = prefs.getMqttUsername()
        password = prefs.getMqttPassword()
        logTopicPrefix = prefs.getMqttLogTopicPrefix()
    }
}