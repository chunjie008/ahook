package com.wzh.ai

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

// --- 导航路由定义 ---
object Routes {
    const val APP_LIST = "appList"
    const val APP_LOG_LIST = "appLogList/{packageName}"
    const val LOG_DETAIL = "logDetail/{logId}"
    const val MQTT_CONFIG = "mqttConfig"

    fun appLogList(packageName: String) = "appLogList/$packageName"
    fun logDetail(logId: Long) = "logDetail/$logId"
}

// --- 主应用导航 ---
@Composable
fun MainApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.APP_LIST) {
        composable(Routes.APP_LIST) {
            AppListScreen(navController)
        }
        composable(
            Routes.APP_LOG_LIST,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName")
            if (packageName != null) {
                AppLogListScreen(navController, packageName)
            }
        }
        composable(
            Routes.LOG_DETAIL,
            arguments = listOf(navArgument("logId") { type = NavType.LongType })
        ) { backStackEntry ->
            val logId = backStackEntry.arguments?.getLong("logId")
            if (logId != null) {
                LogDetailScreen(navController, logId)
            }
        }
        composable(Routes.MQTT_CONFIG) {
            MqttConfigScreen(navController)
        }
    }
}

// --- 一级页面：应用列表 ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun AppListScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var appToDelete by remember { mutableStateOf<AppInfo?>(null) }

    fun refresh() {
        coroutineScope.launch {
            refreshing = true
            apps = getUniqueAppsFromProvider(context)
            refreshing = false
        }
    }

    val pullRefreshState = rememberPullRefreshState(refreshing, ::refresh)

    LaunchedEffect(Unit) {
        refresh()
    }


    
    var showContentProviderHelp by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("已被 Hook 的应用 列表") },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.MQTT_CONFIG) }) {
                        Text("设置")
                    }
                    IconButton(onClick = { showAboutDialog = true }) {
                        Text("关于")
                    }
                }
            ) 
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showContentProviderHelp = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Text("帮助", fontSize = 12.sp)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().pullRefresh(pullRefreshState)) {
            if (apps.isEmpty() && !refreshing) {
                // Wrap in a scrollable container to allow pull-to-refresh even when the list is empty
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("没有找到被 Hook 的应用")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(apps, key = { it.packageName }) {
                        AppInfoItem(
                            appInfo = it,
                            onClick = { navController.navigate(Routes.appLogList(it.packageName)) },
                            onDeleteClick = { appToDelete = it }
                        )
                    }
                }
            }
            PullRefreshIndicator(refreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
        }

        if (appToDelete != null) {
            DeleteConfirmationDialog(
                appName = appToDelete!!.appName,
                onConfirm = {
                    coroutineScope.launch {
                        deleteLogsForPackage(context, appToDelete!!.packageName)
                        appToDelete = null
                        refresh()
                    }
                },
                onDismiss = { appToDelete = null }
            )
        }
    }
    

    
    if (showContentProviderHelp) {
        ContentProviderHelpDialog(
            onDismiss = { showContentProviderHelp = false }
        )
    }
    
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
}


@Composable
fun AppInfoItem(appInfo: AppInfo, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = appInfo.appName, style = MaterialTheme.typography.titleMedium)
                Text(text = appInfo.packageName, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "删除日志", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(appName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = { Text("您确定要删除应用 “$appName” 的所有相关日志吗？此操作不可撤销。") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// --- 二级页面：应用日志列表（带搜索） ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun AppLogListScreen(navController: NavController, packageName: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<LogItem>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf(TextFieldValue("")) }
    var activeSearchQuery by remember { mutableStateOf("") } // This triggers the search

    fun performSearch() {
        activeSearchQuery = searchInput.text
    }

    fun refresh() {
        coroutineScope.launch {
            refreshing = true
            logs = getLogsFromProvider(context, packageName, activeSearchQuery)
            refreshing = false
        }
    }

    val pullRefreshState = rememberPullRefreshState(refreshing, ::refresh)

    // Initial load and subsequent searches
    LaunchedEffect(packageName, activeSearchQuery) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "日志列表") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Search Bar + Button
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    label = { Text("搜索日志内容") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = ::performSearch) {
                    Text("确认")
                }
            }

            Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
                if (logs.isEmpty() && !refreshing) {
                     // Wrap in a scrollable container to allow pull-to-refresh
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                         Text(text = if (activeSearchQuery.isNotBlank()) "没有匹配的搜索结果" else "没有找到日志")
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs, key = { it.id }) { log ->
                            LogSummaryItem(log = log) {
                                navController.navigate(Routes.logDetail(log.id))
                            }
                        }
                    }
                }
                PullRefreshIndicator(refreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/**
 * Truncates the string to the specified length, replacing newlines and adding an ellipsis if truncated.
 */
private fun String?.truncate(maxLength: Int = 60): String {
    if (this == null) return ""
    val cleanText = this.replace("\n", " ").replace("\r", " ").trim()
    return if (cleanText.length > maxLength) {
        cleanText.substring(0, maxLength) + "..."
    } else {
        cleanText
    }
}

@Composable
fun LogSummaryItem(log: LogItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = log.logName, style = MaterialTheme.typography.titleSmall)
            Text(text = log.timestamp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (!log.inputString.isNullOrBlank() || !log.outputString.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!log.inputString.isNullOrBlank()) {
                Text(
                    text = "输入: ${log.inputString.truncate()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }

            if (!log.outputString.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "输出: ${log.outputString.truncate()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
    }
}


// --- 三级页面：日志详情（带堆栈） ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailScreen(navController: NavController, logId: Long) {
    val context = LocalContext.current
    var logItem by remember { mutableStateOf<LogItem?>(null) }

    LaunchedEffect(logId) {
        logItem = getLogById(context, logId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志详情") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        if (logItem == null) {
            Box(modifier = Modifier.fillMaxSize()) { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) }
        } else {
            // Use a scrollable Column with SelectionContainer for better copy-paste experience
            SelectionContainer(modifier = Modifier.padding(padding).fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    DetailRow("时间戳:", logItem!!.timestamp)
                    DetailRow("算法名称:", logItem!!.logName)
                    DetailRow("密钥类型:", logItem!!.keyType)
                    DetailRow("密钥 (String):", logItem!!.keyString)
                    DetailRow("密钥 (Hex):", logItem!!.keyHex)
                    DetailRow("密钥 (Base64):", logItem!!.keyBase64)
                    DetailRow("IV (String):", logItem!!.ivString)
                    DetailRow("IV (Hex):", logItem!!.ivHex)
                    DetailRow("IV (Base64):", logItem!!.ivBase64)
                    DetailRow("输入 (String):", logItem!!.inputString)
                    DetailRow("输入 (Hex):", logItem!!.inputHex)
                    DetailRow("输入 (Base64):", logItem!!.inputBase64)
                    DetailRow("输出 (String):", logItem!!.outputString)
                    DetailRow("输出 (Hex):", logItem!!.outputHex)
                    DetailRow("输出 (Base64):", logItem!!.outputBase64)
                    DetailRow("调用堆栈:", logItem!!.stackTrace, isCode = true)
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String?, isCode: Boolean = false) {
    if (value.isNullOrBlank()) return
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Column(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(value))
                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "复制",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = if (isCode) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
            fontFamily = if (isCode) FontFamily.Monospace else null
        )
    }
}

// --- 数据查询与删除逻辑 ---
private fun getUniqueAppsFromProvider(context: Context): List<AppInfo> {
    val apps = mutableSetOf<AppInfo>()
    val projection = arrayOf(DatabaseHelper.COL_PACKAGE_NAME, DatabaseHelper.COL_APP_NAME)
    val cursor = context.contentResolver.query(LogContentProvider.CONTENT_URI, projection, null, null, "${DatabaseHelper.COL_TIMESTAMP} DESC")
    cursor?.use {
        val packageIndex = it.getColumnIndex(DatabaseHelper.COL_PACKAGE_NAME)
        val appNameIndex = it.getColumnIndex(DatabaseHelper.COL_APP_NAME)
        while (it.moveToNext()) {
            val appName = if(appNameIndex != -1) it.getString(appNameIndex) else null
            val packageName = if(packageIndex != -1) it.getString(packageIndex) else null
            if (appName != null && packageName != null) {
                apps.add(AppInfo(appName, packageName))
            }
        }
    }
    return apps.toList()
}

private fun getLogsFromProvider(context: Context, packageName: String, searchQuery: String = ""): List<LogItem> {
    val logs = mutableListOf<LogItem>()

    val selectionParts = mutableListOf<String>()
    val selectionArgs = mutableListOf<String>()

    selectionParts.add("${DatabaseHelper.COL_PACKAGE_NAME} = ?")
    selectionArgs.add(packageName)

    if (searchQuery.isNotBlank()) {
        val likeQuery = "%$searchQuery%"
        val searchColumns = listOf(
            DatabaseHelper.COL_KEY_STRING,
            DatabaseHelper.COL_KEY_HEX,
            DatabaseHelper.COL_KEY_BASE64,
            DatabaseHelper.COL_IV_STRING,
            DatabaseHelper.COL_IV_HEX,
            DatabaseHelper.COL_IV_BASE64,
            DatabaseHelper.COL_INPUT_STRING,
            DatabaseHelper.COL_INPUT_HEX,
            DatabaseHelper.COL_INPUT_BASE64,
            DatabaseHelper.COL_OUTPUT_STRING,
            DatabaseHelper.COL_OUTPUT_HEX,
            DatabaseHelper.COL_OUTPUT_BASE64
        )
        val searchConditions = searchColumns.joinToString(separator = " OR ") { "$it LIKE ?" }
        selectionParts.add("($searchConditions)")
        searchColumns.forEach { _ -> selectionArgs.add(likeQuery) }
    }

    val finalSelection = selectionParts.joinToString(separator = " AND ")
    val finalSelectionArgs = selectionArgs.toTypedArray()

    val cursor = context.contentResolver.query(LogContentProvider.CONTENT_URI, null, finalSelection, finalSelectionArgs, "${DatabaseHelper.COL_TIMESTAMP} DESC")
    cursor?.use {
        while (it.moveToNext()) {
            logs.add(it.toLogItem())
        }
    }
    return logs
}

private fun getLogById(context: Context, logId: Long): LogItem? {
    val uri = ContentUris.withAppendedId(LogContentProvider.CONTENT_URI, logId)
    val cursor = context.contentResolver.query(uri, null, null, null)
    return cursor?.use { if (it.moveToFirst()) it.toLogItem() else null }
}

private fun deleteLogsForPackage(context: Context, packageName: String) {
    val selection = "${DatabaseHelper.COL_PACKAGE_NAME} = ?"
    val selectionArgs = arrayOf(packageName)
    context.contentResolver.delete(LogContentProvider.CONTENT_URI, selection, selectionArgs)
}

// --- 扩展函数 ---
private fun Cursor.toLogItem(): LogItem {
    fun getStringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index != -1 && !isNull(index)) getString(index) else null
    }

    return LogItem(
        id = getLong(getColumnIndexOrThrow(DatabaseHelper.COL_ID)),
        timestamp = getStringOrNull(DatabaseHelper.COL_TIMESTAMP) ?: "",
        logName = getStringOrNull(DatabaseHelper.COL_LOG_NAME) ?: "",
        packageName = getStringOrNull(DatabaseHelper.COL_PACKAGE_NAME),
        appName = getStringOrNull(DatabaseHelper.COL_APP_NAME),
        keyType = getStringOrNull(DatabaseHelper.COL_KEY_TYPE),
        keyString = getStringOrNull(DatabaseHelper.COL_KEY_STRING),
        keyHex = getStringOrNull(DatabaseHelper.COL_KEY_HEX),
        keyBase64 = getStringOrNull(DatabaseHelper.COL_KEY_BASE64),
        ivString = getStringOrNull(DatabaseHelper.COL_IV_STRING),
        ivHex = getStringOrNull(DatabaseHelper.COL_IV_HEX),
        ivBase64 = getStringOrNull(DatabaseHelper.COL_IV_BASE64),
        inputString = getStringOrNull(DatabaseHelper.COL_INPUT_STRING),
        inputHex = getStringOrNull(DatabaseHelper.COL_INPUT_HEX),
        inputBase64 = getStringOrNull(DatabaseHelper.COL_INPUT_BASE64),
        outputString = getStringOrNull(DatabaseHelper.COL_OUTPUT_STRING),
        outputHex = getStringOrNull(DatabaseHelper.COL_OUTPUT_HEX),
        outputBase64 = getStringOrNull(DatabaseHelper.COL_OUTPUT_BASE64),
        stackTrace = getStringOrNull(DatabaseHelper.COL_STACK_TRACE)
    )
}

// This function is no longer needed as search is done in the provider
// private fun LogItem.contains(query: String): Boolean { ... }



@Composable
fun ContentProviderHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val content = """查询帮助信息：

1. 如何找不到sqlite3 请安装模块：
https://github.com/rojenzaman/sqlite3-magisk-module

2. mcp使用参见项目:
https://github.com/wolfcoming/adb_mcp_server

2. 查询所有的表名称
adb shell "echo \"SELECT name FROM sqlite_master WHERE type='table';\" | su -c 'sqlite3 -json /data/data/com.wzh.ai/databases/hook_logs.db'"

3. 查询表结构
adb shell "echo \"PRAGMA table_info('logs');\" | su -c 'sqlite3 -json /data/data/com.wzh.ai/databases/hook_logs.db'"

4. 某时间段数据示例
adb shell "echo \"SELECT timestamp, log_name, package_name, key_string, key_hex, key_base64, iv_string, iv_hex, iv_base64, input_string, input_hex, input_base64, output_string, output_hex, output_base64 FROM logs WHERE timestamp BETWEEN '2026-02-19 18:08:46.720' AND '2026-02-19 18:08:52.563';\" | su -c 'sqlite3 -json /data/data/com.wzh.ai/databases/hook_logs.db'"

5. 搜索所有包含指定字符串的日志
adb shell "echo \"SELECT timestamp, log_name, package_name, key_string, key_hex, key_base64, iv_string, iv_hex, iv_base64, input_string, input_hex, input_base64, output_string, output_hex, output_base64 FROM logs WHERE (key_string LIKE '%你要搜索的词%' OR key_hex LIKE '%你要搜索的词%' OR key_base64 LIKE '%你要搜索的词%' OR iv_string LIKE '%你要搜索的词%' OR iv_hex LIKE '%你要搜索的词%' OR iv_base64 LIKE '%你要搜索的词%' OR input_string LIKE '%你要搜索的词%' OR input_hex LIKE '%你要搜索的词%' OR input_base64 LIKE '%你要搜索的词%' OR output_string LIKE '%你要搜索的词%' OR output_hex LIKE '%你要搜索的词%' OR output_base64 LIKE '%你要搜索的词%');\" | su -c 'sqlite3 -json /data/data/com.wzh.ai/databases/hook_logs.db'"
"""
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("查询帮助") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(
                        text = content,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Row {
                OutlinedButton(
                    onClick = { 
                        clipboardManager.setText(AnnotatedString(content))
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("复制全部")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onDismiss
                ) {
                    Text("关闭")
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}

// --- 免责声明对话框 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisclaimerDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* 不允许通过点击外部区域关闭对话框 */ },
        title = { Text(text = "法律声明与使用条款") },
        text = {
            Column(
                modifier = Modifier
                    .height(300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "重要提示：使用本工具前请仔细阅读以下条款：",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color.Red
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "1. 本工具仅供合法的安全研究、渗透测试、合规审计及软件开发调试使用。\n\n" +
                           "2. 使用本工具时，您必须确保已获得目标应用所有者的明确书面授权。\n\n" +
                           "3. 请严格遵守所在国家/地区的相关法律法规，包括但不限于《网络安全法》、《个人信息保护法》等。\n\n" +
                           "4. 严禁将本工具用于任何未经授权的渗透、破解、窃取数据或其他违法行为。\n\n" +
                           "5. 本工具可能捕获敏感信息（包括但不限于加密密钥、用户数据等），请严格遵守数据最小化原则，妥善保管捕获的数据，不得泄露给无关第三方。\n\n" +
                           "6. 因不当使用本工具导致的任何法律纠纷，由使用者自行承担全部责任。\n\n" +
                           "7. 开发者保留追究不当使用者法律责任的权利。\n\n" +
                           "8. 使用本工具即表示您同意上述条款，并承诺合法使用。" 
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAgree
            ) {
                Text("我已阅读并同意以上条款")
            }
        },
        dismissButton = {
            Button(
                onClick = onDisagree
            ) {
                Text("不同意，退出应用")
            }
        }
    )
}

// --- 关于对话框 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "关于 AHOOK") },
        text = {
            Column {
                Text(
                    text = "AHOOK - 加密操作监控Xposed模块",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "版本: 1.0")
                Text(text = "作者: water")
                Text(text = "联系QQ: 854978821")
                Text(text = "交流QQ群: 1037044062")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "本工具仅供合法的安全研究、渗透测试、合规审计及软件开发调试使用。",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss
            ) {
                Text("确定")
            }
        }
    )
}

// --- MQTT 配置页面 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MqttConfigScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { PreferencesUtil(context) }

    var brokerUrl by remember { mutableStateOf(prefs.getMqttBrokerUrl()) }
    var username by remember { mutableStateOf(prefs.getMqttUsername()) }
    var password by remember { mutableStateOf(prefs.getMqttPassword()) }
    var topicPrefix by remember { mutableStateOf(prefs.getMqttLogTopicPrefix()) }
    var mqttEnabled by remember { mutableStateOf(prefs.isMqttEnabled()) }

    var showSaveSuccess by remember { mutableStateOf(false) }
    var showSaveError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MQTT 配置") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 启用开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "启用 MQTT 推送",
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = mqttEnabled,
                    onCheckedChange = { enabled ->
                        mqttEnabled = enabled
                        // 立即保存启用状态并尝试连接/断开
                        prefs.setMqttEnabled(enabled)
                        if (enabled) {
                            // 启用时：保存当前配置并启动前台服务
                            prefs.setMqttBrokerUrl(brokerUrl)
                            prefs.setMqttUsername(username)
                            prefs.setMqttPassword(password)
                            prefs.setMqttLogTopicPrefix(topicPrefix)
                            MqttForegroundService.start(context)
                            Toast.makeText(context, "MQTT 服务已启动", Toast.LENGTH_SHORT).show()
                        } else {
                            // 禁用时：停止前台服务
                            MqttForegroundService.stop(context)
                            Toast.makeText(context, "MQTT 服务已停止", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            
            // 后台保活说明
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "后台保活说明",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• 启用后会在通知栏显示常驻通知以保持后台活跃\n" +
                               "• 请在系统设置中关闭本应用的电池优化\n" +
                               "• 部分系统需要允许自启动和后台运行权限",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Broker URL
            OutlinedTextField(
                value = brokerUrl,
                onValueChange = { brokerUrl = it },
                label = { Text("Broker 地址") },
                placeholder = { Text("tcp://your-mqtt-server:1883") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 用户名
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名 (可选)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 密码
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码 (可选)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 主题前缀
            OutlinedTextField(
                value = topicPrefix,
                onValueChange = { topicPrefix = it },
                label = { Text("日志主题前缀") },
                placeholder = { Text("ahook/logs/") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "日志将发送到: ${topicPrefix}all（消息中已包含 packageName）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 保存按钮
            Button(
                onClick = {
                    try {
                        prefs.setMqttEnabled(mqttEnabled)
                        prefs.setMqttBrokerUrl(brokerUrl)
                        prefs.setMqttUsername(username)
                        prefs.setMqttPassword(password)
                        prefs.setMqttLogTopicPrefix(topicPrefix)

                        // 如果启用 MQTT，重启前台服务以应用新配置
                        if (mqttEnabled) {
                            MqttForegroundService.stop(context)
                            MqttForegroundService.start(context)
                        } else {
                            MqttForegroundService.stop(context)
                        }

                        showSaveSuccess = true
                    } catch (e: Exception) {
                        showSaveError = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存配置")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 状态显示（实时刷新）
            var connectionStatus by remember { mutableStateOf(MQTTManager.getInstance().isConnected()) }
            var serviceRunning by remember { mutableStateOf(MqttForegroundService.isServiceRunning()) }
            
            // 定时刷新状态
            LaunchedEffect(Unit) {
                while (true) {
                    connectionStatus = MQTTManager.getInstance().isConnected()
                    serviceRunning = MqttForegroundService.isServiceRunning()
                    kotlinx.coroutines.delay(1000) // 每秒刷新一次
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "运行状态",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (serviceRunning) "● 服务运行中" else "○ 服务已停止",
                            color = if (serviceRunning) Color.Green else Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (connectionStatus) "● MQTT 已连接" else "○ MQTT 未连接",
                            color = if (connectionStatus) Color.Green else Color.Red,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // 保存成功提示
        if (showSaveSuccess) {
            LaunchedEffect(showSaveSuccess) {
                kotlinx.coroutines.delay(2000)
                showSaveSuccess = false
            }
        }

        // 保存失败提示
        if (showSaveError) {
            LaunchedEffect(showSaveError) {
                kotlinx.coroutines.delay(2000)
                showSaveError = false
            }
        }
    }

    // 使用 Toast 显示提示
    if (showSaveSuccess) {
        Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
    }
    if (showSaveError) {
        Toast.makeText(context, "保存失败: ${showSaveError}", Toast.LENGTH_SHORT).show()
    }
}