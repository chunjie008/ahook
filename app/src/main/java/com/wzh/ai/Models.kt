package com.wzh.ai

/**
 * 代表一条记录下来的加密操作日志.
 */
data class LogItem(
    val id: Long = 0,
    val timestamp: String,
    val logName: String,
    val packageName: String? = null,
    val appName: String? = null,
    val keyType: String? = null,
    val keyString: String? = null,
    val keyHex: String? = null,
    val keyBase64: String? = null,
    val ivString: String? = null,
    val ivHex: String? = null,
    val ivBase64: String? = null,
    val inputString: String? = null,
    val inputHex: String? = null,
    val inputBase64: String? = null,
    val outputString: String? = null,
    val outputHex: String? = null,
    val outputBase64: String? = null,
    val stackTrace: String? = null // 新增堆栈追踪字段
)

/**
 * 代表一个被 Hook 过的、独立的应用信息.
 */
data class AppInfo(val appName: String, val packageName: String)
