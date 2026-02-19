package com.wzh.ai

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.Key
import java.security.MessageDigest
import java.security.spec.AlgorithmParameterSpec
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.WeakHashMap
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec

class MainHook : IXposedHookLoadPackage {

    private var appName: String? = null

    // 使用 synchronizedMap 稍微提高一点线程安全性
    private val cipherData = Collections.synchronizedMap(WeakHashMap<Cipher, CipherInfo>())
    private val digestInputs = Collections.synchronizedMap(WeakHashMap<MessageDigest, ByteArrayOutputStream>())
    private val macInputs = Collections.synchronizedMap(WeakHashMap<Mac, ByteArrayOutputStream>())
    private val cipherInputs = Collections.synchronizedMap(WeakHashMap<Cipher, ByteArrayOutputStream>())

    data class CipherInfo(val algorithm: String, val key: Key, val spec: AlgorithmParameterSpec?)

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 过滤系统应用和自身
        if (lpparam.packageName == "android" || lpparam.packageName == "com.wzh.ai" ||
            lpparam.packageName.startsWith("com.android.") || lpparam.packageName.startsWith("com.google.android.")) {
            return
        }

        // Hook Application onCreate 以获取 Context 和 AppName
        hookMethodSafe(Application::class.java, "onCreate") {
            after { param ->
                val application = param.thisObject as Application
                val context = application.applicationContext

                // 获取应用名称
                try {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(lpparam.packageName, 0)
                    appName = pm.getApplicationLabel(appInfo).toString()
                } catch (_: PackageManager.NameNotFoundException) { }

                // 初始化加密 Hook
                hookMessageDigest(lpparam, context)
                hookMac(lpparam, context)
                hookCipher(lpparam, context)
            }
        }
    }

    // ===========================================================================
    // MessageDigest Hook 逻辑
    // ===========================================================================
    private fun hookMessageDigest(lpparam: XC_LoadPackage.LoadPackageParam, context: Context) {
        val updateHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val digest = param.thisObject as MessageDigest
                val stream = digestInputs.getOrPut(digest) { ByteArrayOutputStream() }
                writeToStream(stream, param.args)
            }
        }

        // Hook 所有 update 方法
        hookMethodSafe(MessageDigest::class.java, "update", java.lang.Byte.TYPE, hook = updateHook)
        hookMethodSafe(MessageDigest::class.java, "update", ByteArray::class.java, hook = updateHook)
        hookMethodSafe(MessageDigest::class.java, "update", ByteArray::class.java, Int::class.java, Int::class.java, hook = updateHook)
        hookMethodSafe(MessageDigest::class.java, "update", ByteBuffer::class.java, hook = updateHook)

        // Hook digest 输出结果
        val digestHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                // 如果调用 digest(input)，需要先记录 input
                // 注意：大部分实现内部会调 update，但也可能直接处理，为了保险这里记录一下
                // 配合 afterHookedMethod 的 atomic remove，不会导致重复日志，但可能导致 stream 里有两份数据
                // 更加稳妥的做法是：如果 digest(input) 内部调了 update，数据会在 update 里被记录。
                // 鉴于 MessageDigest 的标准行为，这里我们只依靠 update 收集数据，不在 digest(input) 前 hook 写数据，
                // 除非你确定某些魔改 ROM 的 digest(input) 不调用 update。
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                val digest = param.thisObject as MessageDigest

                // 【核心修复】：原子性移除 Stream。
                // 如果返回 null，说明这个 digest 已经被递归调用的内层 digest() 处理过了，直接 return。
                val stream = digestInputs.remove(digest) ?: return

                val resultBytes = parseResult(param) ?: return
                val inputBytes = stream.toByteArray()

                val values = ContentValues().apply {
                    putCommonValues(this, lpparam, digest.algorithm ?: "MessageDigest")
                    putInputData(this, inputBytes)
                    putOutputData(this, resultBytes)
                }
                insertLog(context, values)
            }
        }
        XposedBridge.hookAllMethods(MessageDigest::class.java, "digest", digestHook)
    }

    // ===========================================================================
    // Mac (HMAC) Hook 逻辑
    // ===========================================================================
    private fun hookMac(lpparam: XC_LoadPackage.LoadPackageParam, context: Context) {
        val updateHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val mac = param.thisObject as Mac
                val stream = macInputs.getOrPut(mac) { ByteArrayOutputStream() }
                writeToStream(stream, param.args)
            }
        }

        hookMethodSafe(Mac::class.java, "update", java.lang.Byte.TYPE, hook = updateHook)
        hookMethodSafe(Mac::class.java, "update", ByteArray::class.java, hook = updateHook)
        hookMethodSafe(Mac::class.java, "update", ByteArray::class.java, Int::class.java, Int::class.java, hook = updateHook)
        hookMethodSafe(Mac::class.java, "update", ByteBuffer::class.java, hook = updateHook)

        val doFinalHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                // doFinal(byte[]) 的情况，记录最后一块数据
                if (param.args.isNotEmpty() && param.args[0] is ByteArray) {
                    val mac = param.thisObject as Mac
                    val stream = macInputs.getOrPut(mac) { ByteArrayOutputStream() }
                    writeToStream(stream, param.args)
                }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                val mac = param.thisObject as Mac
                // 【核心修复】：防止重复日志
                val stream = macInputs.remove(mac) ?: return

                val resultBytes = param.result as? ByteArray ?: return
                val inputBytes = stream.toByteArray()

                // 获取 Key
                var keyBytes: ByteArray? = null
                var keyAlgo = "Unknown"
                try {
                    val key = XposedHelpers.getObjectField(mac, "key") as? Key
                    keyBytes = key?.encoded
                    keyAlgo = key?.javaClass?.name ?: "Unknown"
                } catch (e: Exception) { /* ignore */ }

                val values = ContentValues().apply {
                    putCommonValues(this, lpparam, mac.algorithm ?: "Mac")
                    put(DatabaseHelper.COL_KEY_TYPE, keyAlgo)
                    putKeyData(this, keyBytes)
                    putInputData(this, inputBytes)
                    putOutputData(this, resultBytes)
                }
                insertLog(context, values)
            }
        }
        XposedBridge.hookAllMethods(Mac::class.java, "doFinal", doFinalHook)
    }

    // ===========================================================================
    // Cipher (AES/DES/RSA) Hook 逻辑
    // ===========================================================================
    private fun hookCipher(lpparam: XC_LoadPackage.LoadPackageParam, context: Context) {

        // 1. Hook Init: 捕获 Key, IV, 模式，并清空旧的输入流
        val initHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val cipher = param.thisObject as Cipher
                val key = param.args[1] as? Key
                val spec = param.args.getOrNull(2) as? AlgorithmParameterSpec

                if (key != null) {
                    cipherData[cipher] = CipherInfo(cipher.algorithm, key, spec)
                    // Cipher 复用时，init 意味着新的开始，必须清除旧的输入流
                    cipherInputs.remove(cipher)
                }
            }
        }
        XposedBridge.hookAllMethods(Cipher::class.java, "init", initHook)

        // 2. Hook Update: 持续收集输入数据
        val updateHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val cipher = param.thisObject as Cipher
                // 只有包含 ByteArray 参数的 update 才处理 (忽略 ByteBuffer 暂且，视需求添加)
                if (param.args.isNotEmpty() && param.args[0] is ByteArray) {
                    val stream = cipherInputs.getOrPut(cipher) { ByteArrayOutputStream() }
                    writeToStream(stream, param.args)
                }
            }
        }
        XposedBridge.hookAllMethods(Cipher::class.java, "update", updateHook)

        // 3. Hook doFinal: 处理最后一块数据并输出
        val doFinalHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                // 如果 doFinal 带有输入数据 (doFinal(input))，记录它
                if (param.args.isNotEmpty() && param.args[0] is ByteArray) {
                    val cipher = param.thisObject as Cipher
                    val stream = cipherInputs.getOrPut(cipher) { ByteArrayOutputStream() }
                    writeToStream(stream, param.args)
                }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                val cipher = param.thisObject as Cipher

                // 【核心修复】：remove 原子操作，防止递归调用导致成对日志
                val stream = cipherInputs.remove(cipher) ?: return

                val resultBytes = param.result as? ByteArray ?: return
                val info = cipherData[cipher] ?: return // 如果没有捕获到 init 信息，忽略

                val inputBytes = stream.toByteArray()
                val ivBytes = (info.spec as? IvParameterSpec)?.iv

                val values = ContentValues().apply {
                    putCommonValues(this, lpparam, info.algorithm)
                    put(DatabaseHelper.COL_KEY_TYPE, info.key.javaClass.name)
                    putKeyData(this, info.key.encoded)

                    ivBytes?.let {
                        put(DatabaseHelper.COL_IV_STRING, String(it, Charsets.UTF_8))
                        put(DatabaseHelper.COL_IV_HEX, it.toHexString())
                        put(DatabaseHelper.COL_IV_BASE64, it.toBase64String())
                    }

                    putInputData(this, inputBytes)
                    putOutputData(this, resultBytes)
                }
                insertLog(context, values)
            }
        }
        XposedBridge.hookAllMethods(Cipher::class.java, "doFinal", doFinalHook)
    }

    // ===========================================================================
    // 工具方法
    // ===========================================================================

    // 统一将参数写入流
    private fun writeToStream(stream: ByteArrayOutputStream, args: Array<Any>) {
        if (args.isEmpty()) return
        when (val arg = args[0]) {
            is Byte -> stream.write(arg.toInt())
            is ByteArray -> {
                if (args.size >= 3 && args[1] is Int && args[2] is Int) {
                    // update(byte[], offset, len)
                    val offset = args[1] as Int
                    val len = args[2] as Int
                    stream.write(arg, offset, len)
                } else {
                    // update(byte[])
                    stream.write(arg)
                }
            }
            is ByteBuffer -> {
                val pos = arg.position()
                if (arg.hasRemaining()) {
                    val bytes = ByteArray(arg.remaining())
                    arg.get(bytes)
                    stream.write(bytes)
                    arg.position(pos) // 恢复位置
                }
            }
        }
    }

    // 解析返回值 (处理 int 返回的情况)
    private fun parseResult(param: XC_MethodHook.MethodHookParam): ByteArray? {
        return when (val result = param.result) {
            is ByteArray -> result
            is Int -> {
                // 处理 int digest(byte[], int, int) 这种把结果写回参数的情况
                if (result > 0 && param.args.isNotEmpty() && param.args[0] is ByteArray) {
                    val outputBuf = param.args[0] as ByteArray
                    val offset = param.args[1] as Int
                    val res = ByteArray(result)
                    System.arraycopy(outputBuf, offset, res, 0, result)
                    res
                } else null
            }
            else -> null
        }
    }

    private fun insertLog(context: Context, values: ContentValues) {
        try {
            context.contentResolver.insert(LogContentProvider.CONTENT_URI, values)
        } catch (e: Exception) {
            XposedBridge.log("插入日志失败: ${e.message}")
        }
    }

    private fun getStackTrace(): String {
        return Log.getStackTraceString(Throwable())
    }

    // 填充通用列
    private fun putCommonValues(cv: ContentValues, lpparam: XC_LoadPackage.LoadPackageParam, logName: String) {
        cv.put(DatabaseHelper.COL_TIMESTAMP, SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date()))
        cv.put(DatabaseHelper.COL_LOG_NAME, logName)
        cv.put(DatabaseHelper.COL_PACKAGE_NAME, lpparam.packageName)
        cv.put(DatabaseHelper.COL_APP_NAME, appName)
        cv.put(DatabaseHelper.COL_STACK_TRACE, getStackTrace())
    }

    // 填充输入数据列
    private fun putInputData(cv: ContentValues, bytes: ByteArray) {
        if (bytes.isNotEmpty()) {
            cv.put(DatabaseHelper.COL_INPUT_STRING, String(bytes, Charsets.UTF_8))
            cv.put(DatabaseHelper.COL_INPUT_HEX, bytes.toHexString())
            cv.put(DatabaseHelper.COL_INPUT_BASE64, bytes.toBase64String())
        }
    }

    // 填充输出数据列
    private fun putOutputData(cv: ContentValues, bytes: ByteArray) {
        if (bytes.isNotEmpty()) {
            cv.put(DatabaseHelper.COL_OUTPUT_STRING, String(bytes, Charsets.UTF_8))
        }
        cv.put(DatabaseHelper.COL_OUTPUT_HEX, bytes.toHexString())
        cv.put(DatabaseHelper.COL_OUTPUT_BASE64, bytes.toBase64String())
    }

    // 填充密钥数据列
    private fun putKeyData(cv: ContentValues, bytes: ByteArray?) {
        bytes?.let {
            cv.put(DatabaseHelper.COL_KEY_STRING, String(it, Charsets.UTF_8))
            cv.put(DatabaseHelper.COL_KEY_HEX, it.toHexString())
            cv.put(DatabaseHelper.COL_KEY_BASE64, it.toBase64String())
        }
    }

    // 安全 Hook 封装，简化 Try-Catch
    private fun hookMethodSafe(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Any,
        hook: XC_MethodHook? = null,
        callback: (HookBuilder.() -> Unit)? = null
    ) {
        try {
            val actualHook = hook ?: if (callback != null) {
                val builder = HookBuilder()
                builder.callback()
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) = builder.beforeAction?.invoke(param) ?: Unit
                    override fun afterHookedMethod(param: MethodHookParam) = builder.afterAction?.invoke(param) ?: Unit
                }
            } else return

            XposedHelpers.findAndHookMethod(clazz, methodName, *parameterTypes, actualHook)
        } catch (t: Throwable) {
            XposedBridge.log("Hook $methodName 失败: ${t.message}")
        }
    }

    class HookBuilder {
        var beforeAction: ((XC_MethodHook.MethodHookParam) -> Unit)? = null
        var afterAction: ((XC_MethodHook.MethodHookParam) -> Unit)? = null
        fun before(action: (XC_MethodHook.MethodHookParam) -> Unit) { beforeAction = action }
        fun after(action: (XC_MethodHook.MethodHookParam) -> Unit) { afterAction = action }
    }
}

fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
fun ByteArray.toBase64String(): String = Base64.encodeToString(this, Base64.NO_WRAP)