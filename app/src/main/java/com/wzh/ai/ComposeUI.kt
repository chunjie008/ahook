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

Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("已被 Hook 的应用 列表") }
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